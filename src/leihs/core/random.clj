(ns leihs.core.random)

(def BASE32_CROCKFORD "0123456789ABCDEFGHJKMNPQRSTVWXYZ")

(defn base32-crockford-rand-char []
  (rand-nth BASE32_CROCKFORD))

(defn base32-crockford-rand-str
  ([] (base32-crockford-rand-str 10))
  ([n]
   (->> (repeatedly base32-crockford-rand-char)
        (take n)
        (apply str))))

(comment
  (base32-crockford-rand-char)
  (base32-crockford-rand-str))
