(defproject legba "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [ring/ring-core "1.15.4"]
                 [ring/ring-jetty-adapter "1.15.4"]
                 [prismatic/schema "1.4.1"]
                 [metosin/jsonista "1.0.0"]
                 [com.taoensso/telemere "1.2.1"]
                 [com.github.seancorfield/next.jdbc "1.3.1070"]
                 [org.postgresql/postgresql "42.7.8"]
                 [hikari-cp/hikari-cp "3.3.0"]
                 [com.github.seancorfield/honeysql "2.7.1350"]
                 [cli-matic "0.5.4"]
                 [org.clojure/tools.cli "1.4.256"]]
  :main ^:skip-aot legba.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
