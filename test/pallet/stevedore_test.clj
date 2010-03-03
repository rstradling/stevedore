(ns pallet.stevedore-test
  (:use [pallet.stevedore] :reload-all)
  (:use [pallet.utils :only [bash]]
        clojure.test)
  (:require [clojure.contrib.str-utils2 :as string]))

(defn strip-ws
  "strip extraneous whitespace so tests don't fail because of differences in whitespace"
  [s] (string/trim (string/replace s #"[ ]+" " ")))

(defn strip-line-ws
  "strip extraneous whitespace so tests don't fail because of differences in whitespace"
  [s] (string/trim (string/replace (string/replace s #"\n" " ") #"[ ]+" " ")))

(defmacro bash-out
  "Check output of bash. Macro so that errors appear on the correct line."
  [str]
  `(let [r# (bash ~str)]
    (is (= 0 (:exit r#)))
    (is (= "" (:err r#)))
    (:out r#)))

(deftest number-literal
  (is (= "42" (script 42)))
  (is (= "0.5" (script 1/2))))

(deftest simple-call-test
  (is (= "a b" (script (a b)))))

(deftest call-multi-arg-test
  (is (= "a b c" (script (a b c)))))

(deftest test-arithmetic
  (is (= "(x * y)" (script (* x y)))))

(deftest test-return
  (is (= "return 42" (strip-ws (script (return 42))))))

(deftest test-script-call
  (let [name "name1"]
    (is (= "grep \"^name1\" /etc/passwd"
           (script (grep ~(str "\"^" name "\"") "/etc/passwd"))))))

(deftest test-clj
  (let [foo 42]
    (is (= "42" (script (clj foo))))
    (is (= "42" (script ~foo)))))

(deftest test-str
  (is (= "foobar"
         (script (str foo bar)))))

(deftest test-fn
  (is (= "function foo(x) {\nfoo a\nbar b\n }"
         (strip-ws (script (fn foo [x] (foo a) (bar b)))))))

(deftest test-aget
  (is (= "${foo[2]}" (script (aget foo 2)))))

(deftest test-array
  (is (= "(1 2 \"3\" foo)" (script [1 "2" "\"3\"" :foo]))))

(deftest test-if
  (is (= "if [ \\( \\( foo == bar \\) -a \\( foo != baz \\) \\) ]; then echo fred;fi\n"
         (script (if (&& (== foo bar) (!= foo baz)) (echo fred)))))
  (is (= "fred\n"
         (bash-out (script (if (&& (== foo foo) (!= foo baz)) (echo "fred"))))))
  (is (= "if foo; then\nx=3\nfoo x\nelse\ny=4\nbar y\nfi\n"
         (script (if foo (do (var x 3) (foo x)) (do (var y 4) (bar y))))))
  (is (= "not foo\n"
         (bash-out (script (if (== foo bar) (do (echo "foo")) (do (echo "not foo"))))))))

(deftest test-if-not
  (is (= "if [ ! \\( \\( foo == bar \\) -a \\( foo == baz \\) \\) ]; then echo fred;fi\n"
         (script (if-not (&& (== foo bar) (== foo baz)) (echo fred)))))
  (is (= "fred\n"
         (bash-out (script (if-not (&& (== foo foo) (== foo baz)) (echo "fred")))))))

(deftest test-map
  (is (= "([packages]=(columnchart))" (strip-ws (script {:packages ["columnchart"]}))))
  (is (= "x=([packages]=columnchart)\necho ${x[packages]}"
         (strip-ws (script (do (var x {:packages "columnchart"})
                               (echo (aget x :packages)))))))
  (is (= "columnchart\n"
         (bash-out (script (do (var x {:packages "columnchart"})
                             (echo (aget x :packages))))))))

(deftest test-do
  (is (= "let x=3\nlet y=4\nlet z=(x + y)"
         (strip-ws
	  (script
	   (let x 3)
	   (let y 4)
	   (let z (+ x y))))))
  (is (= "7\n"
         (bash-out
	  (script
	   (let x 3)
	   (let y 4)
	   (let z (+ x y))
           (echo @z))))))

(deftest test-combine-forms
  (let [stuff (quote (do
		       (local x 3)
		       (local y 4)))]
    (is (= "function foo(x) {\nlocal x=3\nlocal y=4\n }"
	   (strip-ws (script (fn foo [x] ~stuff)))))))
