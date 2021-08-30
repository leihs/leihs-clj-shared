(ns leihs.core.log.helpers "Logging macros used in cljs.")

(defmacro spy [expr]
  `(let [res# ~expr]
     (js/console.log res#)
     res#))

(defmacro spy-with [func expr]
  `(let [res# ~expr]
     (js/console.log (~func res#))
     res#))
