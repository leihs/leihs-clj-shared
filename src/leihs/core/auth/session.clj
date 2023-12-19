(ns leihs.core.auth.session
  (:refer-clojure :exclude [str keyword])
  (:require
    ;[clojure.java.jdbc :as jdbc]

    [leihs.procurement.utils.helpers :refer [my-cast]]

    [leihs.core.auth.shared :refer [access-rights]]
    [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sqlo]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]

    [taoensso.timbre :refer [debug info warn error spy]]


    [cheshire.core :refer [generate-string] :rename
     {generate-string to-json}]

    ;; all needed imports
    [honey.sql :refer [format] :rename {format sql-format}]
    [leihs.core.db :as db]
    [next.jdbc :as jdbc-next]
    ;[next.jdbc :as jdbc]
    [honey.sql.helpers :as sql]

    [pandect.core]
    )
  (:import
    [java.util UUID]
    ))

(def user-select
  [:users.email
   :users.firstname
   :users.id
   :users.is_admin
   :users.language_locale
   :users.lastname
   :users.login
   :users.org_id
   :users.is_system_admin
   [:users.id :user_id]
   [(-> (sql/select :%count.*)
        (sql/from :contracts)
        (sql/where [:= :contracts.user_id :users.id]))
    :contracts_count]
   [(-> (sql/select :%count.*)
        (sql/from :access_rights)
        (sql/where [:= :access_rights.user_id :users.id]))
    :inventory_pool_roles_count]])

(defn user-with-valid-session-query [session-token]
  (-> (apply sql/select user-select)
      (sql/select
        [:user_sessions.id :user_session_id]
        [:user_sessions.created_at :user_session_created_at]
        [:authentication_systems.external_sign_out_url :external_sign_out_url])

      (sql/from :users)
      (sql/join :user_sessions [:= :users.id :user_id])
      (sql/join :authentication_systems
                [:= :authentication_systems.id :user_sessions.authentication_system_id])
      (sql/join :system_and_security_settings [:= :system_and_security_settings.id 0])

      (sql/where [:= :user_sessions.token_hash [[:encode [[:digest session-token "sha256"]] "hex"]]])

      (sql/where [:raw (str "now() < user_sessions.created_at + "
                            "system_and_security_settings.sessions_max_lifetime_secs * interval '1 second'")])

      (sql/where [:= :account_enabled true])

      ;
      ;
      ;(sql/returning :*)
      sql-format
      ;spy
      ))


(comment

  (let [
        tx (db/get-ds-next)
        request {:route-params {:user-id #uuid "c0777d74-668b-5e01-abb5-f8277baa0ea8"}
                 :tx tx}
        user-id #uuid "37bb3d3d-3a61-4f98-863e-c549568317f0"

        ;session-token "36b82f6e-cb73-4452-9f60-668659cc747c"

        ;; cookie / leihs-user-session=d16211b5-df89-44e0-b433-bb5728fdaf89
        user-session "d16211b5-df89-44e0-b433-bb5728fdaf89"

        p (println ">o> session-token" session-token)

        query (spy (user-with-valid-session-query (spy user-session)))
        p (println ">o> query" query)

        ;query (-> query
        ;
        ;          ;(sql/returning :*)
        ;          sql-format
        ;          )

        p (println ">o> query" query)
        user (spy (jdbc-next/execute-one! tx query))

        p (println "\nuser=" user)

        ]

    )
  )



(defn authenticated-user-entity [session-token {tx :tx-next :as request}]


  ;(try
  (when-let [

             ;user (->> (user-with-valid-session-query session-token)
             ;          (jdbc-next/execute-one! tx))
             ;
             ;query (spy (user-with-valid-session-query (spy session-token)))
             ;user (spy(jdbc-next/execute-one! tx query ))
             ;
             user (let [
                        p (println ">o> session-token" session-token)

                        query (spy (user-with-valid-session-query (spy session-token)))

                        p (println ">o> query" query)
                        user (spy (jdbc-next/execute-one! tx query))
                        p (println ">o> user" user)
                        ] user)

             ]

    (assoc (spy user)
      :authentication-method :session
      :access-rights (spy (access-rights tx (:id user)))
      :scope_read true
      :scope_write true
      :scope_admin_read (:is_admin user)
      :scope_admin_write (:is_admin user)
      :scope_system_admin_read (:is_system_admin user)
      :scope_system_admin_write (:is_system_admin user))
    ))

(defn session-token [request]
  (some-> request :cookies
          (get USER_SESSION_COOKIE_NAME nil) :value))

(defn- authenticate [request]
  (println ">oo> authenticate")
  (catcher/snatch
    {:level :warn
     :return-expr request}
    (if-let [user (spy (some-> request
                               session-token
                               (authenticated-user-entity request)))]
      (assoc request :authenticated-entity (spy user))
      request)))

(defn wrap-authenticate [handler]
  (fn [request]
    (-> request authenticate handler)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;(ns leihs.my.back.html
;    (:refer-clojure :exclude [keyword str])
;    (:require
;      [hiccup.page :refer [html5]]
;      [honey.sql :refer [format] :rename {format sql-format}]
;      [honey.sql.helpers :as sql]
;      [leihs.core.http-cache-buster2 :as cache-buster]
;      [leihs.core.json :refer [to-json]]
;      [leihs.core.remote-navbar.shared :refer [navbar-props]]
;      [leihs.core.shared :refer [head]]
;      [leihs.core.url.core :as url]
;      [leihs.my.authorization :as auth]
;      [leihs.core.db :as db]
;      [next.jdbc :as jdbc]))

(comment
  (let [
        tx (db/get-ds-next)
        request {:route-params {:user-id #uuid "c0777d74-668b-5e01-abb5-f8277baa0ea8"}
                 :tx tx}
        user-id #uuid "37bb3d3d-3a61-4f98-863e-c549568317f0"
        user-id #uuid "47da3d53-703d-444e-81e9-f3c653fcdc9b"
        user-id #uuid "c0777d74-668b-5e01-abb5-f8277baa0ea8"

        ;user [:id user-id]
        user {:id user-id}

        sql (-> (sql/delete-from :user_sessions)
                ;(sql/where ["user_id = ?" (:id user)])
                (sql/where [:= :user_id [:cast (:id user) :uuid]])
                (sql/returning :*)
                sql-format
                spy
                )
        p (println ">oo> sql=" sql)

        result (jdbc-next/execute-one! tx sql)
        p (println ">oo> sql=" result)

        ]
    )
  )




;
;(def updated-session-data (assoc user-session-data :meta_data new-meta-data))

(comment


  (let [
        tx (db/get-ds-next)
        request {:route-params {:user-id #uuid "c0777d74-668b-5e01-abb5-f8277baa0ea8"}
                 :headers {:host "example.com"
                           "user-agent" "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                           "accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                           "accept-language" "en-US,en;q=0.5"}
                 :remote-addr "140.82.121.4:443"
                 :tx tx}
        user-id #uuid "37bb3d3d-3a61-4f98-863e-c549568317f0"
        user-id #uuid "47da3d53-703d-444e-81e9-f3c653fcdc9b"
        user-id #uuid "c0777d74-668b-5e01-abb5-f8277baa0ea8"


        p (println ">o> ??? 1" (get-in request [:headers "user-agent"]))
        p (println ">o> ??? 2" (get-in request [:remote-addr]))

        ;user [:id user-id]
        token "abc-def"
        user {:id user-id}
        authentication_system_id "abc"

        token-hash (pandect.core/sha256 token)
        user-session-data {:user_id (:id user)
                           :token_hash token-hash
                           :authentication_system_id authentication_system_id
                           :meta_data {:user_agent (get-in request [:headers "user-agent"])
                                       :remote_addr (get-in request [:remote-addr])}}

        meta_data (-> (:meta_data user-session-data)
                      to-json)

        user-session-data (assoc user-session-data :meta_data meta_data)

        sql (-> (sql/insert-into :user_sessions)
                (sql/values [(my-cast user-session-data)])
                (sql/returning :*)
                sql-format
                spy
                )
        p (println ">oo> sql=" sql)

        result (jdbc-next/execute-one! tx sql)
        p (println ">oo> sql=" result)

        ]
    )
  )







(defn create-user-session
  [user authentication_system_id {:as request tx :tx-next settings :settings}]
  "Create and returns the user_session. The map includes additionally
  the original token to be used as the value of the session cookie."
  (when (:sessions_force_uniqueness settings)

    ;(jdbc/delete! tx :user_sessions ["user_id = ?" (:id user)])
    (spy (let [
               p (println ">oo> user=" user)

               sql (-> (sql/delete-from :user_sessions)
                       ;(sql/where ["user_id = ?" (:id user)])
                       (sql/where [:= :user_id (:id user)])
                       (sql/returning :*)
                       sql-format
                       spy
                       )

               p (println ">oo> sql=" sql)


               result (jdbc-next/execute-one! tx sql)
               p (println ">oo> sql=" result)

               ] result)
         )
    )

  (let [token (str (UUID/randomUUID))
        token-hash (pandect.core/sha256 token)
        user-session-data {:user_id (:id user)
                           :token_hash token-hash
                           :authentication_system_id authentication_system_id
                           :meta_data {:user_agent (get-in request [:headers "user-agent"])
                                       :remote_addr (get-in request [:remote-addr])}}

        meta_data (-> (:meta_data user-session-data)
                      to-json)

        user-session-data (assoc user-session-data :meta_data meta_data)

        user-session (spy (jdbc-next/execute-one! tx (-> (sql/insert-into :user_sessions)
                                                         (sql/values [(my-cast user-session-data)])
                                                         (sql/returning :*)
                                                         sql-format
                                                         spy
                                                         )))
        p (println ">oo> user-session" user-session)

        ]
    (assoc user-session :token token)))





;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
