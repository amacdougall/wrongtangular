(defproject wrongtangular "0.1.0-SNAPSHOT"
  :description "Quick image categorization webapp."

  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/clojurescript "0.0-3211"]
                 [org.omcljs/om "0.8.8"]
                 [sablono "0.3.4"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.3.3"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} [:target-path "out" "resources/public/js"]
  
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :figwheel {:on-jsload "wrongtangular.core/main"}
              :compiler {:output-to "resources/public/js/wrongtangular.js"
                         :output-dir "resources/public/js/out"
                         :optimizations :none
                         :main wrongtangular.core
                         :asset-path "js/out"
                         :source-map true
                         :source-map-timestamp true
                         :cache-analysis true }}
             {:id "min"
              :source-paths ["src"]
              :compiler {:output-to "resources/public/js/wrongtangular.js"
                         :main wrongtangular.core                         
                         :optimizations :advanced
                         :pretty-print false}}]}

  :figwheel {:http-server-root "public" ;; default and assumes "resources"
             :server-port 3449 ;; default
             :css-dirs ["resources/public/css"]}) ;; watch and update CSS
