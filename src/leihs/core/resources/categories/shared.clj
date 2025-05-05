(ns leihs.core.resources.categories.shared
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.core :refer [drop-keys flatten-once]]
   [leihs.core.db :as db]
   [leihs.core.resources.images.core :as images]
   [next.jdbc.sql :as jdbc]))

; NOTE: `category_id` alias due to requirements of FE plugin irt uniqueness of `id`s.
; As a category may appear in form of multiple nodes (under different label) in the
; category tree, this uniqueness is not guaranteed. Therefore, we use `category_id`.
(def fields [[:model_groups.id :category_id]
             :model_groups.name])

(def base-query
  (-> (apply sql/select fields)
      (sql/from :model_groups)
      (sql/where [:= :model_groups.type "Category"])
      (sql/order-by :model_groups.name)))

(defn metadata-conf [label]
  {"id" :model_groups.id
   "name" :model_groups.name
   "label" label
   "models_count" (-> (sql/select :%count.*)
                      (sql/from :model_links)
                      (sql/where [:=
                                  :model_links.model_group_id
                                  :model_groups.id]))
   "is_deletable" [:not-exists (-> (sql/select true)
                                   (sql/from :model_links)
                                   (sql/where [:=
                                               :model_links.model_group_id
                                               :model_groups.id]))]
   "image_url" [:|| images/IMG-DATA-URL-PREFIX "," :images.content]
   "thumbnail_url" [:|| images/IMG-DATA-URL-PREFIX "," :thumbnails.content]})

(defn sql-add-metadata [query & {:keys [label exclude] :or {exclude []}}]
  (let [fields (-> (metadata-conf label)
                   (drop-keys (map name exclude))
                   vec flatten-once)]
    (-> query
        (sql/select [(cons :json_build_object fields) :metadata])
        (sql/left-join :images
                       [:and
                        [:= :images.target_id :model_groups.id]
                        [:= :images.thumbnail false]])
        (sql/left-join [:images :thumbnails]
                       [:and
                        [:= :thumbnails.target_id :model_groups.id]
                        [:= :thumbnails.thumbnail true]]))))
