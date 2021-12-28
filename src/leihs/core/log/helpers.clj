(ns leihs.core.log.helpers "Logging macros used in cljs.")

; TODO should be removed, doesn't support namespaces or
; logging level; timbre does all that withhas `spy`,
; do we need how about `spy-with`

(defmacro spy [expr]
  `(let [res# ~expr]
     (js/console.log res#)
     res#))

(defmacro spy-with [func expr]
  `(let [res# ~expr]
     (js/console.log (~func res#))
     res#))
