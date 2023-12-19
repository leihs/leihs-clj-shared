(ns leihs.core.auth.shared
  (:refer-clojure :exclude [str keyword])
  (:require

    ;; all needed imports
    [honey.sql :refer [format] :rename {format sql-format}]
    [leihs.core.db :as db]
    [next.jdbc :as jdbc]
    [honey.sql.helpers :as sql]


        [taoensso.timbre :refer [debug info warn error spy]]

    ;[clojure.java.jdbc :as jdbc]
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sqlo]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

;; TODO

(defn access-rights [tx user-id]
  (spy (-> (sql/select :role :inventory_pool_id)
           (sql/from :access_rights)
           (sql/where [:= :user_id [:cast user-id :uuid]])
           sql-format
           (->> (jdbc/execute-one! tx)))))

(defn password-hash
  [password tx]
  (->> [[:crypt [[:cast password :text]]
         [[:raw "gen_salt('bf', 10)"]]
         ] :pw_hash]
       sql/select
       sql-format
       (jdbc/execute-one! tx)
       spy
       :pw_hash))


(comment

  (let [
        tx (db/get-ds-next)
        ;query (password-hash "abc" tx )
        query (access-rights tx "726a9fb9-8016-4efd-9cd8-6ed4da37c026"  )

        p (println "\nquery" query)
        ]

       )
  )


;sql/format
;(jdbc/query tx)
;first
;:pw_hash))
