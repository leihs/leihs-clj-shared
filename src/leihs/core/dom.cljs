(ns leihs.core.dom
  (:refer-clojure :exclude [str keyword])
  (:require

    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.url.core :as url]

    [camel-snake-kebab.core :refer [->camelCase]]
    [goog.dom :as dom]
    [goog.dom.dataset :as dataset]
    ))

(defn data-attribute
  "Retrieves JSON and urlencoded data attribute with attribute-name
  from the first element with element-name."
  [element-name attribute-name]
  (try (-> (.getElementsByTagName js/document element-name)
           (aget 0)
           (dataset/get (->camelCase attribute-name))
           url/decode
           (#(.parse js/JSON %))
           cljs.core/js->clj
           clojure.walk/keywordize-keys)
       (catch js/Object e
         (js/console.log e)
         nil)))

