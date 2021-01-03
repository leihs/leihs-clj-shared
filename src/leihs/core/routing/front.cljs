(ns leihs.core.routing.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [leihs.core.defaults :as defaults]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.url.query-params :as query-params]
    [leihs.core.paths :refer [path]]
    [leihs.core.icons :as icons]

    [accountant.core :as accountant]
    [bidi.bidi :as bidi]
    [cljs.core.async :refer [timeout]]
    [clojure.pprint :refer [pprint]]
    [reagent.core :as reagent]
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
        path (.getPath uri)
        {route-params :route-params
         handler-key :handler} (match-path path)
        query (.getQuery uri)
        fragment (.getFragment uri)]
    {:url href
     :path path
     :handler-key handler-key
     :route-params route-params
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
                             :route-params route-params
                             :handler-key handler-key
                             :page (resolve-page handler-key)
                             :url location-href
                             :path (.getPath location-url)
                             :query-params-raw (-> location-url .getQuery
                                                   (query-params/decode :parse-json? false))
                             :query-params (-> location-url .getQuery
                                               query-params/decode))
                      ;(js/console.log (with-out-str (pprint [handler-key route-params])))
                      ))
     :path-exists? (fn [path]
                     ;(js/console.log (with-out-str (pprint (match-path path))))
                     (boolean (when-let [handler-key (:handler (match-path path))]
                                (when-not (handler-key @external-handlers*)
                                  handler-key))))}))

(defn form-per-page-component []
  (let [per-page (or (some-> @state* :query-params :per-page presence)
                     defaults/PER-PAGE)
        hk (some-> @state* :handler-key)
        route-params (or (some-> @state* :route-params) {})
        query-parameters (some-> @state* :query-params-raw) ]
    [:div.form-group.ml-2.mr-2.mt-2
     [:label.mr-1 {:for :per-page} "Per page"]
     [:select#per-page.form-control
      {:value per-page
       :tab-index 100
       :on-change (fn [e]
                    (let [val (int (or (some-> e .-target .-value presence)
                                       defaults/PER-PAGE))]
                      (accountant/navigate!
                        (path hk route-params
                              (merge query-parameters
                                     {:page 1
                                      :per-page val})))))}
      (for [p defaults/PER-PAGE-VALUES]
        [:option {:key p :value p} p])]]))

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



;;; ;;;;;;;;;;;;;;;;;;;;;;;;;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delayed-query-params-input-component
  [& {:keys [input-options query-params-key label prepend]
      :or {input-options {}
           query-params-key "replace-me"
           label "LABEL"
           prepend nil}}]
  (let [value* (reagent/atom "")]
    (fn [& _]
      [:div.form-group.m-2
       [hidden-state-component
        {:did-change #(reset! value* (-> @state* :query-params-raw query-params-key))}]
       [:label {:for query-params-key} [:span label [:small.text-monospace " (" query-params-key ")"]]]
       [:div.input-group
        (when prepend [prepend])
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
          icons/delete]]]])))

;;; form-term-filter-component ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce term-value* (reagent/atom ""))

(defn form-term-filter-on-change-handler [event default-query-params]
  (let [newval (or (some-> event .-target .-value presence) "")]
    (reset! term-value* newval)
    (go (<! (timeout 350))
        (when (= @term-value* newval)
          (accountant/navigate!
            (current-path-for-query-params
              default-query-params
              {:page 1 :term @term-value*}))))))

(defn form-term-filter-component
  [& {:keys [:default-query-params :input-options :placeholder]
      :or {default-query-params {}
           input-options {}
           placeholder "fuzzy term"}}]
  [:div.form-group.ml-2.mr-2.mt-2.col-md-3
   [hidden-state-component
    {:did-mount #(reset! term-value* (-> @state* :query-params-raw :term))}]
   [:label {:for :term} "Search"]
   [:input#term.form-control.mb-1.mr-sm-1.mb-sm-0
    (merge
      {:type :text
       :placeholder placeholder
       :tab-index 1
       :value @term-value*
       :on-change #(form-term-filter-on-change-handler % default-query-params)}
      input-options)]])


;;; form reset component ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-reset-component [& {:keys [default-query-params]
                               :or {default-query-params nil}}]
  [:div.form-group.mt-2
   [:label {:for :reset-query-params} "Reset filters"]
   [:div
    [:button#reset-query-params.btn.btn-outline-warning
     {:tab-index 1
      :on-click #(do
                   (reset! term-value* "")
                   (accountant/navigate!
                     (path (:handler-key @state*)
                           (:route-params @state*)
                           (if default-query-params
                             (merge (:query-params-raw @state*)
                                    default-query-params)
                             {}))))}
     [:i.fas.fa-times]
     " Reset "]]])

(def navigate! accountant/navigate!)
