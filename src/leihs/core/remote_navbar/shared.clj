(ns leihs.core.remote-navbar.shared
  (:require [clojure.java.jdbc :as jdbc]
            [leihs.core
             [paths :refer [path]]
             [sql :as sql]]
            [leihs.core.anti-csrf.back :refer [anti-csrf-token]]
            [leihs.core.user.permissions
             :refer
             [borrow-access? managed-inventory-pools]]
            [leihs.core.user.permissions.procure :as procure]))

(defn- languages
  [tx]
  (-> (sql/select :*)
      (sql/from :languages)
      (sql/where [:= :active true])
      sql/format
      (->> (jdbc/query tx))))

(defn- sub-apps
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
  [auth-entity]
  (if auth-entity
    {:user {:id (:user_id auth-entity),
            :firstname (:firstname auth-entity),
            :lastname (:lastname auth-entity),
            :login (:login auth-entity),
            :email (:email auth-entity)
            :selectedLocale (:language_id auth-entity)}}))

(defn navbar-props
  [request]
  (let [csrf-token (anti-csrf-token request)
        tx (:tx request)
        auth-entity (:authenticated-entity request)]
    {:config {:appTitle "Leihs",
              :appColor "gray",
              :csrfToken csrf-token,
              :me (user-info auth-entity),
              :subApps (sub-apps tx auth-entity),
              :locales (languages tx)}}))
