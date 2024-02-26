(ns leihs.core.db.type-conversion
  (:require
   [clojure.data.json :as json]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as jdbc-rs]
   [taoensso.timbre :refer [debug error info spy warn]])
  (:import
   [java.sql Array PreparedStatement]
   [org.postgresql.util PGobject]))

;;; PostgreSQL Arrays ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-protocol jdbc-rs/ReadableColumn
  Array
  (read-column-by-label [^Array v _] (vec (.getArray v)))
  (read-column-by-index [^Array v _ _] (vec (.getArray v))))

;;; PostgreSQL JSON ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ->json json/write-str)

(def <-json #(json/read-str % :key-fn keyword))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn json-to-string [data]
  (try
    (let [json-str (json/write-str data)]
      (println "JSON string generated successfully.")
      json-str)
    (catch Exception e
      (println "Failed to convert data to JSON string." (.getMessage e))
      nil)))

(defn valid-json? [s]
  (try
    (clojure.data.json/read-str s)
    (println ">o> yes, valid-json")
    true
    (catch Exception e
      false)))

(defn json-to-clojure [json-str]
  (json/read-str json-str :key-fn keyword))

(defn string-to-json [s]
  (try
    (let [json (clojure.data.json/read-str s :key-fn keyword)]
      (println "CAUTION: JSON string parsed successfully, process DB-Data-Cleanup in ")
      json)
    (catch Exception e
      (println "Invalid JSON string")
      s)))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (<-json value) {:pgtype type}))
      value)))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type (.getType v)
        value (.getValue v)

        ;; fix
        ;value (json-to-clojure value)
        ;p (println ">o> abc??? val=" value)

        ;; correct (with fixed)
        ;vv (with-meta (<-json value) {:pgtype type})

        vv (try (with-meta (<-json value) {:pgtype type})
                (catch Exception e
                  (println "CAUTION: Conversion did not work.")

                  (if (valid-json? value)
                    (do
                      ;; if value is a valid JSON string, try to convert it
                      (println "CAUTION: Value is a valid JSON string. Trying to convert it.")
                      (println "CAUTION: Investigations needed, seems to be a data-quality issue (wrong format).")
                      (println "CAUTION: :pgtype=" type)
                      (println "CAUTION: :value=" value)
                      (with-meta (<-json (json-to-clojure value)) {:pgtype type}))
                    ;; return value as default
                    value)))
        p (println ">o> abc???" vv)
        p (println ">o> abc??? class=" (class vv))
        ;>o> abc??? [{:PNG:Compression Deflate/Inflate, :System:FilePermissions -rw-r--r--, :System:FileModifyDate 2024:02:22 17:40:09+01:00, :PNG-pHYs:PixelsPerUnitY 443, :ExifTool:ExifToolVersion 12.7, :Composite:Megapixels 0.004, :System:Directory /var/folders/jl/49spzkdd2v3cdz37fgmp0ddr6cj76v/T, :PNG:Palette (Binary data 207 bytes, use -b option to extract), :System:FileSize 841 bytes, :PNG:ImageHeight 64, :File:FileTypeExtension png, :File:FileType PNG, :PNG:BitDepth 8, :Composite:ImageSize 64x64, :PNG:ColorType Palette, :PNG:Interlace Noninterlaced, :PNG:ImageWidth 64, :PNG:Software www.inkscape.org, :File:MIMEType image/png, :PNG:SignificantBits 8 8 8, :System:FileInodeChangeDate 2024:02:22 17:40:09+01:00, :SourceFile /var/folders/jl/49spzkdd2v3cdz37fgmp0ddr6cj76v/T/ring-multipart-7309565735486786405.tmp, :System:FileAccessDate 2024:02:22 17:40:09+01:00, :PNG-pHYs:PixelsPerUnitX 443, :System:FileName ring-multipart-7309565735486786405.tmp, :PNG:Transparency (Binary data 68 bytes, use -b option to extract), :PNG-pHYs:PixelUnits meters, :PNG:Filter Adaptive}]
        ;>o> abc??? class= clojure.lang.PersistentVector
        ]
    (if (#{"jsonb" "json"} type)
      (when value
        ;(with-meta (<-json value) {:pgtype type}))

        vv)
      ;(<-json value))                                     ;; slashed quotes
      ;(string-to-json value))
      ;value)                                              ;;double slash
      value)))

(defn <-pgobject__
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type (.getType v)
        value (.getValue v)

        cljdata (json-to-clojure (string-to-json value))

        ;p (println ">o> abc???" (<-json value))
        p (println ">o> abc???" cljdata)
        p (println ">o> abc???" (<-json cljdata))]
    (if (#{"jsonb" "json"} type)
      (when value
        ;(with-meta (<-json value) {:pgtype type}))
        ;(with-meta (<-json cljdata) {:pgtype type}))
        (with-meta (string-to-json cljdata) {:pgtype type}))
      ;(with-meta (<-json (json-to-string (string-to-json value))) {:pgtype type}))
      ;(with-meta (string-to-json value) {:pgtype type}))
      ;(with-meta value {:pgtype type}))
      ;(string-to-json value))
      ;value)
      value)))

(defn <-pgobject_
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when (spy value)
        (let [p (println ">o> gen_res0" value)
              p (println ">o> gen_res1" (class value))

              ;result (if (valid-json? value)
              ;            (string-to-json value)
              ;            value)

              ;p (println ">o> new_res0" value_new)
              ;p (println ">o> new_res1" (class value_new))
              ;result (with-meta (<-json value_new) {:pgtype type})
              ;p (println ">o> new_res2" result)
              ;p (println ">o> new_res3" (class result))

;; original
              result (try (with-meta (<-json value) {:pgtype type})
                          (catch Exception e
                            (println "Invalid JSON string, pgtype=" type)
                            ;(with-meta (<-json (string-to-json value)) {:pgtype type})
                            ;(with-meta (string-to-json value) {:pgtype type})
                            (string-to-json value)))

;result (with-meta (<-json value) {:pgtype type})
              ;p (println ">o> old_res2" result)
              ;p (println ">o> old_res3" (class result))

              p (println ">o> final result _> 5: " result)
              p (println ">o> final result _> 6: " (class result))]

          result

          ;(if (instance? String json-expected)
          ;  json-expected
          ;  (with-meta (<-json value) {:pgtype type})))
          ))
      (do
        (println ">o> fuuuuck" value)
        value))))

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol jdbc-rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))
