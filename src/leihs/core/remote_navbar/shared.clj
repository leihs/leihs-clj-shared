(ns leihs.core.remote-navbar.shared
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [leihs.core [paths :refer [path]] [sql :as sql]]
            [leihs.core.anti-csrf.back :refer [anti-csrf-token]]
            [leihs.core.user.permissions :refer
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
  [tx auth-entity locales]
  (when auth-entity
    (let [user (-> (sql/select :*)
                   (sql/from :users)
                   (sql/where [:= :id (:user_id auth-entity)])
                   sql/format
                   (->> (jdbc/query tx))
                   first)
          default-language (first (filter :default locales))]
      {:user {:id (:id user),
              :firstname (:firstname user),
              :lastname (:lastname user),
              :login (:login user),
              :email (:email user),
              :selectedLocale (or (:language_id user)
                                  (:id default-language))}})))

(defn navbar-props
  [request]
  (let [csrf-token (anti-csrf-token request)
        tx (:tx request)
        auth-entity (:authenticated-entity request)
        locales (languages tx)]
    {:config {:appTitle "leihs",
              :appColor "gray",
              :csrfToken csrf-token,
              :me (user-info tx auth-entity locales),
              :subApps (sub-apps tx auth-entity),
              :locales locales}}))
