(ns user
  (:require
   [clojure.tools.namespace.repl :as repl]
   [shadow.cljs.devtools.server :as server]
   [shadow.cljs.devtools.api :as api]))


(repl/set-refresh-dirs "dev" "src" "examples")


(def page (str
"<!doctype html>
<html>
  <head>
    <meta http-equiv='content-type' content='text/html;charset=UTF-8'/>
    <title>aye-db test page</title>
  </head>

  <body>
    <script src='js/main.js' type='text/javascript'></script>
  </body>
</html>"))


(defn gen
  []
  (spit "target/index.html" page))


(defn go
  []
  (gen)
  (server/start!)
  (api/watch :aye-db))


(defn halt!
  []
  (server/stop!))


(defn reset
  []
  (repl/refresh)
  (gen))


(defn cljs
  []
  (api/repl :aye-db))
