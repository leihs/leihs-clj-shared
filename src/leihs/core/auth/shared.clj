(ns leihs.core.auth.shared
  (:refer-clojure :exclude [keyword str])
  (:require
    [clojure.java.jdbc :as jdbc]

    [honey.sql :as sqlh]
    [honey.sql.helpers :as h]
    [next.jdbc :as jdbc-next]
    [leihs.core.db :as db]

    [leihs.core.sql :as sql]
    ))

(defn access-rights ^:deprecated [tx user-id]
  (-> (sql/select :role :inventory_pool_id)
      (sql/from :access_rights)
      (sql/merge-where [:= :user_id user-id])
      sql/format
      (->> (jdbc/query tx))))

(defn access-rights-new [tx user-id]
  (-> (h/select :role :inventory_pool_id)
      (h/from :access_rights)
      (h/where [:= :user_id user-id])
      sqlh/format
      (->> (jdbc-next/execute! tx))))

(defn password-hash ^:deprecated
  [password tx]
  (->> [(sql/call :crypt
                  (sql/call :cast password :text)
                  (sql/raw "gen_salt('bf', 10)"))
        :pw_hash]
       sql/select
       sql/format
       (jdbc/query tx)
       first
       :pw_hash))

(defn password-hash-new
  ([password tx]
   (->> ["SELECT crypt(?,gen_salt('bf',10)) AS pw_hash" password]
        (jdbc-next/execute-one! tx)
        :pw_hash)))

(comment
  (let [
        tx-next (db/get-ds-next)
        tx (db/get-ds)

        ;res1 (access-rights-old tx #uuid "02563014-d74d-5572-a8b2-3736738459ba"  )
        ;res1 (access-rights tx-next #uuid "02563014-d74d-5572-a8b2-3736738459ba")
        ;p (println "\n\n>res1>> " res1 "\n\n")

        ;[SELECT crypt(CAST(? AS text), gen_salt('bf', 10)) AS pw_hash  password]
        ;res1 (password-hash "password" tx )

        res1 (password-hash-new "password" tx-next)
        p (println "\n\n>res1>> " res1 "\n\n")
        ])
  )
