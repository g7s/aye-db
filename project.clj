(defproject g7s/aye-db "0.1.0"
  :description "ClojureScript wrapper for IndexedDB"
  :url "https://github.com/g7s/aye-db"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies
  [[org.clojure/clojure "1.10.1" :scope "provided"]
   [org.clojure/clojurescript "1.10.597" :scope "provided"]
   [funcool/promesa "5.1.0"]]

  :source-paths
  ["src"]

  :profiles
  {:dev {:dependencies [[com.google.javascript/closure-compiler-unshaded "v20190325"]
                        [org.clojure/google-closure-library "0.0-20190213-2033d5d9"]
                        [thheller/shadow-cljs "2.8.90"]]}
   :repl {:source-paths ["dev"]
          :dependencies [[org.clojure/tools.namespace "0.3.1"]]
          :repl-options {:init-ns user
                         :nrepl-middleware
                         [shadow.cljs.devtools.server.nrepl04/middleware]}}})
