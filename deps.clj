(def shared-deps
  '[[aleph "0.4.6"]
    [bidi "2.1.3"]
    [buddy/buddy-sign "3.0.0"]
    [camel-snake-kebab "0.4.0"]
    [cheshire "5.8.0"]
    [clj-http "3.9.0"]
    [clj-pid "0.1.2"]
    [cljs-http "0.1.45"]
    [cljsjs/jimp "0.2.27"]
    [cljsjs/js-yaml "3.3.1-0"]
    [cljsjs/moment "2.22.2-0"]
    [clojure-humanize "0.2.2"]
    [com.github.mfornos/humanize-slim "1.2.2"]
    [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
    [compojure "1.6.1"]
    [environ "1.1.0"]
    [hiccup "1.0.5"]
    [hickory "0.7.1"]
    [hikari-cp "2.6.0"]
    [honeysql "0.9.3"]
    [inflections "0.13.0"]
    [io.dropwizard.metrics/metrics-core "4.0.3"]
    [io.dropwizard.metrics/metrics-healthchecks "4.0.3"]
    [io.forward/yaml "1.0.9"]
    [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
    [logbug "4.2.2"]
    [me.raynes/conch "0.8.0"]
    [nilenso/honeysql-postgres "0.2.4"]
    [org.clojure/clojure "1.9.0"]
    [org.clojure/clojurescript "1.10.339" :scope "provided"]
    [org.clojure/java.jdbc "0.7.7"]
    [org.clojure/tools.cli "0.3.7"]
    [org.clojure/tools.logging "0.4.1"]
    [org.clojure/tools.nrepl "0.2.13"]
    [org.slf4j/slf4j-log4j12 "1.7.25"]
    [pandect "0.6.1"]
    [pg-types "2.4.0-PRE.1"]
    [reagent "0.8.1"]
    [ring "1.6.3"]
    [ring-middleware-accept "2.0.3"]
    [ring/ring-json "0.4.0"]
    [spootnik/signal "0.2.1"]
    [timothypratley/patchin "0.3.5"]
    [uritemplate-clj "1.2.1"]
    [venantius/accountant "0.2.4"]

    ; force transitive dependency resolution
    [ring/ring-core "1.6.3"]
    [com.google.guava/guava "23.0"]])

(defn extend-shared-deps [deps]
  (reduce (fn [acc dep]
            (if (some #(= (first dep) (first %)) acc)
              acc
              (conj acc dep)))
          deps
          shared-deps))
