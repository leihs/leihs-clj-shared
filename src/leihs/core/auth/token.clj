(ns leihs.core.auth.token
  (:refer-clojure :exclude [str keyword])
  (:require
    [clj-time.core :as time]


    [clojure.java.jdbc :as jdbco]

    ;; all needed imports
    [honey.sql :refer [format] :rename {format sql-format}]
    [leihs.core.db :as db]
    [next.jdbc :as jdbc]
    [honey.sql.helpers :as sql]

    [clojure.walk :refer [keywordize-keys]]
    [leihs.core.auth.shared :refer [access-rights]]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.ring-exception :as ring-exception]
    [leihs.core.sql :as sqlo]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown])
  (:import
    [java.util Base64]))

(defn token-error-page [exception request]
  (-> {:status 401
       :body (str "Token authentication error: "
                  (.getMessage exception))}))

(defn token-matches-clause [token-secret]
  ([:= :api_tokens.token_hash [:crypt token-secret :api_tokens.token_hash]] ))

(defn user-with-valid-token-query [token-secret]
  (-> (sql/select
        :scope_read
        :scope_write
        :scope_admin_read
        :scope_admin_write
        :scope_system_admin_read
        :scope_system_admin_write
        [:users.id :id]
        [:users.id :user_id]
        :is_admin :account_enabled :firstname :lastname :email :is_system_admin
        [:api_tokens.id :api_token_id]
        [:api_tokens.created_at :api_token_created_at])
      (sql/from :users)
      (sql/join :api_tokens [:= :users.id :user_id])
      (sql/where (token-matches-clause token-secret))
      (sql/where [:= :account_enabled true])
      (sql/where [:raw (str "now() < api_tokens.expires_at")])
      sql/format))



(defn user-auth-entity! [token-secret tx]
  p (println ">o> user-auth-entity!" token-secret)

  (if-let [
           ;uae (spy (->> (user-with-valid-token-query token-secret)
           ;              ;(jdbc/query tx) first))]
           ;
           ;
           ;              (jdbc/execute-one! tx)))


           uae (let [
                     query (user-with-valid-token-query token-secret)
                     p (println ">o> query" query)

                     result (jdbc/execute-one! tx query)
                     p (println ">o> result" result)
                     ]result )

           ]
    (assoc uae
      :authentication-method :token
      :access-rights (access-rights tx (:user_id uae))
      :scope_admin_read (and (:scope_admin_read uae) (:is_admin uae))
      :scope_admin_write (and (:scope_admin_write uae) (:is_admin uae))
      :scope_system_admin_read (and (:scope_system_admin_read uae) (:is_system_admin uae))
      :scope_system_admin_write (and (:scope_system_admin_write uae) (:is_system_admin uae)))
    (throw (ex-info
             (str "No valid API-Token / User combination found! "
                  "Is the token present, not expired, and the user permitted to sign-in?") {}))))

(defn- decode-base64
  [^String string]
  (apply str (map char (.decode (Base64/getDecoder) (.getBytes string)))))

(defn extract-token-value [request]
  (when-let [auth-header (get-in request [:headers "authorization"])]
    (or (some->> auth-header
          (re-find #"(?i)^token\s+(.*)$")
          last presence)
        (some->> auth-header
          (re-find #"(?i)^basic\s+(.*)$")
          last presence decode-base64
          (#(clojure.string/split % #":" 2))
          (map presence) (filter identity)
          last))))

(defn authenticate [{tx :tx-next :as request}
                    _handler]
  (catcher/snatch
    {:level :warn
     :return-fn (fn [e] (token-error-page e request))}
    (let [handler (ring-exception/wrap _handler)]
      (if-let [token-secret (extract-token-value request)]
        (let [user-auth-entity (user-auth-entity! token-secret tx)]
          (handler (assoc request :authenticated-entity user-auth-entity)))
        (handler request)))))

(defn wrap-authenticate [handler]
  (fn [request]
    (authenticate request handler)))

;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns 'cider-ci.open-session.encryptor)
;(debug/debug-ns *ns*)
