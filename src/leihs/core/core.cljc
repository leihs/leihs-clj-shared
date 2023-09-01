(ns leihs.core.core
  (:refer-clojure :exclude [str keyword])
  (:require [clojure.string :refer [trim-newline]]))

(defn str
  "Like clojure.core/str but maps keywords to strings without preceding colon."
  ([] "")
  ([x]
   (if (keyword? x)
     (subs (clojure.core/str x) 1)
     (clojure.core/str x)))
  ([x & yx]
   (apply clojure.core/str  (concat [(str x)] (apply str yx)))))

(defn keyword
  "Like clojure.core/keyword but coerces an unknown single argument x
  with (-> x cider-ci.utils.core/str cider-ci.utils.core/keyword).
  In contrast clojure.core/keyword will return nil for anything
  not being a String, Symbol or a Keyword already (including
  java.util.UUID, Integer)."
  ([name] (cond (keyword? name) name
                :else (clojure.core/keyword (str name))))
  ([ns name] (clojure.core/keyword ns name)))

(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn presence [v]
  "Returns nil if v is a blank string or if v is an empty collection.
   Returns v otherwise."
  (cond
    (string? v) (if (clojure.string/blank? v) nil v)
    (coll? v) (if (empty? v) nil v)
    :else v))

(defn presence? [v]
  "Checks if v is not a blank string or if v is not an empty collection.
  Otherwise checks if not nil."
  (cond
    (string? v) (not (clojure.string/blank? v))
    (coll? v) (not (empty? v))
    :else (not (nil? v))))

(defn presence! [v]
  "Pipes v through presence returns the result of that iff it is not nil.
  Throws an IllegalStateException otherwise. "
  (or (-> v presence)
      (throw
       (new
        #?(:clj IllegalStateException
           :cljs js/Error)
        "The argument must neither be nil, nor an empty string nor an empty collection."))))

(defn flip [f]
  (fn [& xs]
    (apply f (reverse xs))))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn remove-nils
  "Dissociate all keys from the map where the value is nil."
  [m]
  (into {} (filter second m)))

(defn remove-blanks
  "Dissociate all keys from the map where the value is blank."
  [m]
  (->> m
       (filter (fn [[_ v]] (presence v)))
       (into {})))

(defn raise
  ([msg] (throw (ex-info msg {})))
  ([msg m] (throw (ex-info msg m)))
  ([msg m c] (throw (ex-info msg m c))))

(defn detect
  "Returns first element x for any x in coll where (pred x)
  returns logical true value, else nil."
  [pred coll]
  (first (filter pred coll)))

(defn drop-at [i v]
  "Removes an element from a vector at given index."
  (concat (take i v) (drop (inc i) v)))
