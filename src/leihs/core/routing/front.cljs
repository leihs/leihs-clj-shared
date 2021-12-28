(ns leihs.core.routing.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [leihs.core.constants :as constants]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.defaults :as defaults]
    [leihs.core.icons :as icons]
    [leihs.core.paths :refer [path]]
    [leihs.core.url.core :as url]
    [leihs.core.url.query-params :as query-params]

    [accountant.core :as accountant]
    [bidi.bidi :as bidi]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :refer [timeout]]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    [reagent.core :as reagent]
    [taoensso.timbre :as logging]
    [timothypratley.patchin :as patchin])
  (:import goog.Uri))


(def paths* (reagent/atom nil))

(def resolve-table* (reagent/atom nil))

(def external-handlers* (reagent/atom nil))

(defonce state* (reagent/atom {}))

(def current-url* (reaction (:url @state*)))

(defn hidden-state-component [handlers]
  "handlers is a map of keys to functions where the keys :did-mount,
  :did-update, etc correspond to the react lifcycle methods.
  The custom :did-change will fire routing state changes including did-mount.
  In contrast do :did-update, :did-change will not fire when other captured state changes."
  (let [old-state* (reagent/atom nil)
        eval-did-change (fn [handler args]
                          (let [old-state @old-state*
                                new-state @state*]
                            (when (not= old-state new-state)
                              (reset! old-state* new-state)
                              (apply handler (concat
                                               [old-state (patchin/diff old-state new-state) new-state]
                                               args)))))]
    (reagent/create-class
      {:component-will-unmount (fn [& args] (when-let [handler (:will-unmount handlers)]
                                            (apply handler args)))
       :component-did-mount (fn [& args]
                              (when-let [handler (:did-mount handlers)]
                                (apply handler args))
                              (when-let [handler (:did-change handlers)]
                                (eval-did-change handler args)))
       :component-did-update (fn [& args]
                               (when-let [handler (:did-update handlers)]
                                 (apply handler args))
                               (when-let [handler (:did-change handlers)]
                                 (eval-did-change handler args)))
       :reagent-render
       (fn [_]
         [:div.hidden-routing-state-component
          {:style {:display :none}}
          [:pre (with-out-str (pprint @state*))]])})))

(defn resolve-page [k]
  (get @resolve-table* k nil))

(defn match-path [path]
  (bidi/match-route @paths* path))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn dissect-href [href]
  (let [uri (.parse Uri href)
        path (.getPath ^js uri)
        {route-params :route-params
         handler-key :handler} (match-path path)
        query (.getQuery ^js uri)
        fragment (.getFragment ^js uri)]
    {:url href
     :path path
     :handler-key handler-key
     :route-params (url/decode-keys route-params)
     :query-params-raw (query-params/decode query :parse-json? false)
     :query-params (query-params/decode query :parse-json? true)
     :fragment fragment}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn init-navigation []
  (accountant/configure-navigation!
    {:nav-handler (fn [path]
                    (let [{route-params :route-params
                           handler-key :handler} (match-path path)
                          location-href (-> js/window .-location .-href)
                          location-url (goog.Uri. location-href)]
                      (swap! state* assoc
                             :id (uuid/uuid-string (uuid/make-random-uuid))
                             :route-params (url/decode-keys route-params)
                             :handler-key handler-key
                             :page (resolve-page handler-key)
                             :url location-href
                             :path (.getPath location-url)
                             :route (str (.getPath location-url)
                                         (when-let [query (-> location-url .getQuery presence)]
                                           (str "?" query)))
                             :query-params-raw (-> location-url .getQuery
                                                   (query-params/decode :parse-json? false))
                             :query-params (-> location-url .getQuery
                                               query-params/decode))))
     :path-exists? (fn [path]
                     ;(js/console.log (with-out-str (pprint (match-path path))))
                     (boolean (when-let [handler-key (:handler (match-path path))]
                                (when-not (handler-key @external-handlers*)
                                  handler-key))))}))

(defn current-path-for-query-params
  [default-query-params new-query-params]
  (path (:handler-key @state*)
        (:route-params @state*)
        (merge default-query-params
               (:query-params-raw @state*)
               new-query-params)))

(defn init [paths resolve-table external-handlers]
  "paths: definition of paths, see bidi;
  resolve-table: mapping from handler-keys to handlers,
  external-handlers: will trigger a full page reload, used for handler-keys
  defined in pahts but not in resolve-table"
  (reset! paths* paths)
  (reset! resolve-table* resolve-table)
  (reset! external-handlers* external-handlers)
  (init-navigation)
  (accountant/dispatch-current!))


;;; Filter Components ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delayed-query-params-input-component
  [& {:keys [input-options query-params-key label prepend prepend-args classes]
      :or {input-options {}
           classes []
           query-params-key "replace-me"
           label "LABEL"
           prepend nil
           prepend-args []}}]
  (let [value* (reagent/atom "")]
    (fn [& _]
      [:div.form-group.my-2
       {:class (->> classes (map str) (string/join " "))}
       [hidden-state-component
        {:did-change #(reset! value* (-> @state* :query-params-raw query-params-key))}]
       [:label {:for query-params-key} [:span label [:small.text-monospace " (" query-params-key ")"]]]
       [:div.input-group
        (when prepend [apply prepend prepend-args])
        [:input.form-control
         (merge
           {:id query-params-key
            :value @value*
            :tab-index 1
            :placeholder query-params-key
            :on-change (fn [e]
                         (let [newval (or (some-> e .-target .-value presence) "")]
                           (reset! value* newval)
                           (go (<! (timeout 500))
                               (when (= @value* newval)
                                 (accountant/navigate!
                                   (path (:handler-key @state*)
                                         (:route-params @state*)
                                         (merge {}
                                                (:query-params-raw @state*)
                                                {:page 1
                                                 query-params-key newval})))))))}
           input-options)]
        [:div.input-group-append
         [:button.btn.btn-outline-warning
          {:on-click (fn [_]
                       (reset! value* "")
                       (accountant/navigate!
                         (path (:handler-key @state*)
                               (:route-params @state*)
                               (merge {}
                                      (:query-params-raw @state*)
                                      {:page 1
                                       query-params-key ""}))))}
          [icons/delete]]]]])))

(defn form-term-filter-component
  [& {:keys [input-options query-params-key label prepend classes]
      :or {input-options {}
           query-params-key :term
           label "Search"
           prepend nil
           classes [:col-md-3]}}]
  [delayed-query-params-input-component
   :label label
   :classes classes
   :query-params-key :term
   :input-options {:placeholder "fuzzy term"}
   :prepend nil])


(defn user-choose-prepend-component
  [& {:keys [text query-params-key] :or {text "Choose"}}]
  [:div.input-group-prepend
   [:a.btn.btn-info
    {:tab-index constants/TAB-INDEX
     :href (path :users-choose {}
                 {:return-to (:url @state*)
                  :query-params-key query-params-key})}
    [:span
     [:i.fas.fa-rotate-90.fa-hand-pointer.px-2]
     " " text " "]]])

(defn choose-user-component
  [& {:keys [input-options query-params-key label choose-text classes]
      :or {input-options {}
           query-params-key :user-uid
           label "User"
           choose-text "Choose"
           classes [:col-md-3]}}]
  [:div.col-md-4
   [delayed-query-params-input-component
    :label label
    :query-params-key query-params-key
    :input-options input-options
    :prepend user-choose-prepend-component
    :prepend-args [:text choose-text
                   :query-params-key query-params-key]]])

(defn select-component
  [& {:keys [options default-option query-params-key label classes]
      :or {label "Select"
           query-params-key :select
           classes []}}]
  (let [options (cond
                  (map? options) (->> options
                                      (map (fn [[k v]] [(str k) (str v)]))
                                      (into {}))
                  (sequential? options) (->> options
                                             (map (fn [k] [(str k) (str k)]))
                                             (into {}))
                  :else {"" ""})
        default-option (or default-option
                           (-> options first first))]
    [:div.form-group.m-2
     [:label {:for query-params-key}
      [:span label [:small.text-monospace " (" query-params-key ")"]]]
     [:div.input-group
      [:select.form-control
       {:id query-params-key
        :value (let [val (get-in @state* [:query-params-raw query-params-key])]
                 (if (some #{val} (keys options))
                   val
                   default-option))
        :on-change (fn [e]
                     (let [val (or (-> e .-target .-value presence) "")]
                       (accountant/navigate!
                         (path (:handler-key @state*)
                               (:route-params @state*)
                               (merge {}
                                      (:query-params-raw @state*)
                                      {:page 1
                                       query-params-key val})))))}
       (for [[k n] options]
         [:option {:key k :value k} n])]
      [:div.input-group-append
       [:button.btn.btn-outline-warning
        {:on-click (fn [_]
                     (accountant/navigate!
                       (path (:handler-key @state*)
                             (:route-params @state*)
                             (merge {}
                                    (:query-params-raw @state*)
                                    {:page 1
                                     query-params-key default-option}))))}
        [icons/delete]]]]]))


;;; pagination ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-per-page-component []
  [select-component
   :label "Per page"
   :query-params-key :per-page
   :options (map str defaults/PER-PAGE-VALUES)
   :default-option (str defaults/PER-PAGE)])

(defn pagination-component []
  (let [hk (some-> @state* :handler-key)
        route-params (or (some-> @state* :route-params) {})
        query-parameters (some-> @state* :query-params-raw)
        current-page (or (some-> query-parameters :page int) 1)]
    (if-not hk
      [:div "pagination not ready"]
      [:div.clearfix.mt-2.mb-2
       ;(console.log 'HK (clj->js hk))
       (let [ppage (dec current-page)
             ppagepath (path hk route-params
                             (assoc query-parameters :page ppage))]
         [:div.float-left
          [:a.btn.btn-outline-primary.btn-sm
           {:class (when (< ppage 1) "disabled")
            :href ppagepath}
           [:i.fas.fa-arrow-circle-left] " previous " ]])
       (let [npage (inc current-page)
             npagepath (path hk route-params
                             (assoc query-parameters :page npage))]
         [:div.float-right
          [:a.btn.btn-outline-primary.btn-sm
           {:href npagepath}
           " next " [:i.fas.fa-arrow-circle-right]]])])))


;;; form reset component ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-reset-component [& {:keys [default-query-params]
                               :or {default-query-params nil}}]
  [:div.form-group.m-2
   [:label {:for :reset-query-params} "Reset all filters"]
   [:div
    [:button#reset-query-params.btn.btn-outline-warning
     {:tab-index 1
      :on-click #(do (accountant/navigate!
                       (path (:handler-key @state*)
                             (:route-params @state*)
                             (if default-query-params
                               (merge (:query-params-raw @state*)
                                      default-query-params)
                               {}))))}
     [:i.fas.fa-times]
     " Reset "]]])

(def navigate! accountant/navigate!)
