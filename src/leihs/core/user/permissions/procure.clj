(ns leihs.core.user.permissions.procure
  (:require
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn admin?
  [tx auth-entity]
  (:result
    (first
      (jdbc-query
        tx
        (-> (sql/select
              [[:exists
                (-> (sql/select true)
                    (sql/from :procurement_admins)
                    (sql/where [:= :procurement_admins.user_id
                                (:user_id auth-entity)]))]
               :result])
            sql-format)))))

(defn inspector?
  ([tx auth-entity] (inspector? tx auth-entity nil))
  ([tx auth-entity c-id]
   (:result
     (first
       (jdbc-query
         tx
         (-> (sql/select
               [[:exists
                 (cond-> (-> (sql/select true)
                             (sql/from :procurement_category_inspectors)
                             (sql/where
                               [:= :procurement_category_inspectors.user_id
                                (:user_id auth-entity)]))
                   c-id (sql/where
                          [:= :procurement_category_inspectors.category_id
                           c-id]))]
                :result])
             sql-format))))))

(defn viewer?
  ([tx auth-entity] (viewer? tx auth-entity nil))
  ([tx auth-entity c-id]
   (:result
     (first (jdbc-query
              tx
              (-> (sql/select
                    [[:exists
                      (cond-> (-> (sql/select true)
                                  (sql/from :procurement_category_viewers)
                                  (sql/where
                                    [:= :procurement_category_viewers.user_id
                                     (:user_id auth-entity)]))
                        c-id (sql/where
                               [:= :procurement_category_viewers.category_id
                                c-id]))]
                     :result])
                  sql-format))))))

(defn requester?
  [tx auth-entity]
  (:result
    (first
      (jdbc-query
        tx
        (-> (sql/select
              [[:exists
                (-> (sql/select true)
                    (sql/from :procurement_requesters_organizations)
                    (sql/where
                      [:= :procurement_requesters_organizations.user_id
                       (:user_id auth-entity)]))]
               :result])
            sql-format)))))

(defn advanced?
  [tx auth-entity]
  (->> [viewer? inspector? admin?]
       (map #(% tx auth-entity))
       (some true?)))

(defn any-access?
  [tx auth-entity]
  (->> [requester? viewer? inspector? admin?]
       (map #(% tx auth-entity))
       (some true?)))
