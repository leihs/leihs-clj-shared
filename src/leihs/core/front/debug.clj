; macros part of the same-named-namespace
; see: https://clojurescript.org/guides/ns-forms#_implicit_sugar

(ns leihs.core.front.debug)

(defmacro spy [expr]
  `(let [res# ~expr]
     (js/console.log res#)
     res#))

(defmacro spy-with [func expr]
  `(let [res# ~expr]
     (js/console.log (~func res#))
     res#))
