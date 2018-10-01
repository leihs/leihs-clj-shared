(ns leihs.core.routing.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.url.query-params :as query-params]

    [bidi.bidi :as bidi]
    [accountant.core :as accountant]
    [clojure.pprint :refer [pprint]]
    [reagent.core :as reagent]
    [timothypratley.patchin :as patchin]
    ))


(def paths* (reagent/atom nil))

(def resolve-table* (reagent/atom nil))

(def external-handlers* (reagent/atom nil))

(defonce state* (reagent/atom {}))

(defn hidden-state-component [handlers]
  "handlers is a map of keys to functions where the keys :will-mount,
  :did-mount, :did-update correspond to the react lifcycle methods.
  The custom :did-change will fire routing state changes including did-mount.
  In contrast do :did-mount, :did-change will not fire when other captured state changes."
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
      {:component-will-mount (fn [& args] (when-let [handler (:will-mount handlers)]
                                            (apply handler args)))
       :component-will-unmount (fn [& args] (when-let [handler (:will-unmount handlers)]
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


(def navigate! accountant/navigate!)
