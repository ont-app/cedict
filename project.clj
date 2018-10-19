(defproject cedict "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.apache.jena/jena-core "3.9.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [org.clojure/tools.cli "0.4.0"]
                 [selmer "1.12.1"]
                 ]
  :main ^:skip-aot cedict.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
