(ns leihs.core.resources.categories.tree
  #?(:clj
     (:require
      [honey.sql :refer [format] :rename {format sql-format}]
      [honey.sql.helpers :as sql]
      [leihs.core.resources.categories.descendents :refer [descendents]]
      [leihs.core.resources.categories.shared :refer [base-query sql-add-metadata]]
      [next.jdbc.sql :as jdbc])))

#?(:clj
   (defn roots
     [tx & {:keys [with-metadata exclude] :or {with-metadata false}}]
     (-> base-query
         (cond-> with-metadata
           (sql-add-metadata :label nil :exclude exclude))
         (sql/where
          [:not
           [:exists
            (-> (sql/select 1)
                (sql/from :model_group_links)
                (sql/where [:= :model_group_links.child_id :model_groups.id]))]])
         sql-format
         (->> (jdbc/query tx)))))

#?(:clj
   (defn tree [tx & {:keys [with-metadata exclude] :or {with-metadata false}}]
     (map #(descendents tx %
                        :with-metadata with-metadata
                        :exclude exclude)
          (roots tx
                 :with-metadata with-metadata
                 :exclude exclude))))

(defn convert-tree-path
  "Converts a tree path represented as a map into a tree path
  represented as a vector (list of ancestors incl. self).
  Example:
  ;; (convert-tree-path {:id 1 :children [{:id 2 :children [{:id 3}]}]})
  ;; => [{:id 1} {:id 2} {:id 3}]"
  [node]
  (letfn [(tree-path-h [node result]
            (if (empty? (:children node))
              (conj result node)
              (tree-path-h (first (:children node))
                           (conj result (dissoc node :children)))))]
    (tree-path-h node [])))

