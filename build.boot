(def project 'relman)

(def version "0.1.0")

(set! *warn-on-reflection* true)

(set-env! :resource-paths #{"resources"}
          :source-paths   #{"src"}
          :dependencies   '[[org.clojure/clojure "1.10.1"]
                            [manifold "0.1.8"]
                            [aleph "0.4.6"]
                            [ring "1.7.1"]
                            [ring/ring-json "0.5.0"]
                            [compojure "1.6.1"]
                            [com.fasterxml.jackson.core/jackson-core "2.9.9"]
                            [failjure "1.5.0"]
                            [org.postgresql/postgresql "42.2.8"]
                            [ragtime "0.8.0"]
                            [hikari-cp "2.9.0"]
                            [com.layerware/hugsql "0.5.1"]
                            [metosin/compojure-api "2.0.0-alpha30"]
                            [prismatic/schema "1.1.12"]
                            [lispyclouds/clj-docker-client "0.3.2"]
                            [mount "0.1.16"]
                            [environ "1.1.0"]
                            [com.taoensso/timbre "4.10.0"]
                            [javax.xml.bind/jaxb-api "2.3.0"]           ;; For Aleph's XML dependency, Java 8 compat
                            [io.netty/netty-all "4.1.36.Final"]         ;; Forced Netty version for Java 9+ compat
                            [javax.activation/activation "1.1.1"]       ;; Java 9+ compat for XML bindings
                            ;; Test
                            [lambdaisland/kaocha-boot "0.0-14" :scope "test"]
                            [org.clojure/test.check "0.10.0" :scope "test"]
                            ;; Plugins
                            [boot-deps "0.1.9" :scope "test"]])

(task-options!
 aot {:all true}
 pom {:project     project
      :version     version
      :description "This is what CI/CD should've been."
      :url         "https://relman-cd.github.io/relman"
      :scm         {:url "https://github.com/relman-cd/relman"}
      :license     {"GPL 3.0"
                    "https://www.gnu.org/licenses/gpl-3.0.en.html"}}
 jar  {:main       'relman.main
       :file       (str project "-standalone.jar")})

(def compiler-opts
  {:direct-linking true
   :elide-meta     [:doc :file :line :added]})

(deftask build
  "Perform a full AOT Uberjar build"
  [d dir PATH #{str} "Directories to write to (target)."]
  (binding [clojure.core/*compiler-options* compiler-opts]
    (let [dir (if (seq dir) dir #{"target"})]
      (comp (aot)
            (pom)
            (uber)
            (jar)
            (target :dir dir)))))

(deftask run
  "Start -main"
  [a args ARG [str] "CLI args to Relman."]
  (require '[relman.main :as app])
  (apply (resolve 'app/-main) args)
  (wait))

(require '[kaocha.boot-task :refer [kaocha]])

;(deftask strest
;  "Run integraion tests"
;  []
;  (sh "docker-compose"  "-f" "integration-tests/docker-compose.yaml"
;      "up" "--abort-on-container-exit" "integration-tests")
;  (sh "docker-compose"  "-f" "integration-tests/docker-compose.yaml" "down"))


