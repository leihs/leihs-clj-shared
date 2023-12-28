(ns leihs.core.remote-navbar.shared
  (:require
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
    [clojure.set :as set]
    [leihs.core [paths :refer [path]]]
    [leihs.core.anti-csrf.back :refer [anti-csrf-props]]
    [leihs.core.constants :as constants]
    [leihs.core.locale2 :refer [get-selected-language]]
    [leihs.core.user.permissions :refer [borrow-access? managed-inventory-pools]]
    [leihs.core.user.permissions.procure :as procure]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]))

(defn- languages
  [tx]
  (-> (sql/select :*)
      (sql/from :languages)
      (sql/where [:= :active true])
      sql-format
      (->> (jdbc-query tx))))

(defn sub-apps
  [tx auth-entity]
  (if auth-entity
    (merge {:borrow (borrow-access? tx auth-entity)}
           {:admin (:is_admin auth-entity)}
           {:procure (procure/any-access? tx auth-entity)}
           {:manage (map #(hash-map :name (:name %)
                                    :href (path :daily
                                                {:inventory_pool_id (:id %)}))
                      (managed-inventory-pools tx auth-entity))})))

(defn- user-info
  [tx auth-entity locales]
  (when auth-entity
    (let [user (-> (sql/select :*)
                   (sql/from :users)
                   (sql/where [:= :id (:user_id auth-entity)])
                   sql-format
                   (->> (jdbc-query tx))
                   first)
          default-language (first (filter :default locales))]
      {:user {:id (:id user),
              :firstname (:firstname user),
              :lastname (:lastname user),
              :login (:login user),
              :email (:email user)}})))

(defn navbar-props
  ([request] (navbar-props request {}))
  ([request subapps-override]
   (let [tx (:tx-next request)
         auth-entity (:authenticated-entity request)
         user-language (get-selected-language request)
         locales (map #(as-> % <>
                        (dissoc <> [:active])
                        (set/rename-keys <> {:default :isDefault})
                        (assoc <> :isSelected (= (:locale %) (:locale user-language))))
                   (languages tx))]
     (merge
      (anti-csrf-props request)
      {:config {:appTitle "leihs",
               :appColor "gray",
               :me (user-info tx auth-entity locales),
               :subApps (-> (sub-apps tx auth-entity)
                            (merge subapps-override)),
               :locales locales}}))))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
