(ns g7s.aye-db
  (:require
   [clojure.walk :refer [prewalk]]
   [g7s.aye-db.protocols :refer [create-transaction
                                 execute-query
                                 commit-transaction]]))


(def write-ops #{:idb/put :idb/add :idb/delete :idb/clear})


(defmacro with-tx
  {:style/indent 2}
  [db exec-sym & body]
  (let [exec-fn (gensym "exec__")
        stores  (volatile! #{})
        mode    (volatile! :r)
        body    (prewalk (fn [form]
                           (when (and (vector? form) (= exec-sym (first form)))
                             (throw (ex-info (str "Illegal use of " (name exec-sym)) {:form form})))
                           (if (and (list? form) (= exec-sym (first form)))
                             (let [[store qspec :as query] (second form)]
                               (when (contains? write-ops (first qspec))
                                 (vreset! mode :rw))
                               (vswap! stores conj store)
                               (list exec-fn query))
                             form))
                         body)
        tx-opts {:mode   @mode
                 :stores @stores}]
    `(promesa.core/let [db# ~db
                        err# (atom nil)
                        tx# (create-transaction db# ~tx-opts)
                        ~exec-fn #(-> (execute-query db# tx# %)
                                      (promesa.core/catch
                                          (fn [e#]
                                            (when-not @err# (reset! err# e#)))))
                        v# (promesa.core/do! ~@body)]
       (-> (commit-transaction db# tx#)
           (promesa.core/then' (fn [] v#))
           (promesa.core/catch (fn [e#] (promesa.core/rejected (or @err# e#))))))))
