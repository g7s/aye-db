(ns g7s.aye-db
  (:require
   [promesa.core :as p]
   [g7s.aye-db.protocols :as proto])
  (:require-macros
   [g7s.aye-db :refer [with-tx]]))


(def ^:private TX_DONE (js/WeakMap.))


(defn- ->mode
  [kw]
  (case kw
    (:r nil) "readonly"
    :rw      "readwrite"))


(defn ->source
  [src]
  (condp instance? src
    js/IDBObjectStore [:store (.-name src)]
    js/IDBIndex       [:index (.-name src)]
    js/IDBCursor      [:cursor (->source (.-source src))]
    [:unknown nil]))


(defn- idb-op-error
  [req [qtype :as query-spec] reason]
  (ex-info (str "Failed on " (name qtype)  " operation.")
           {:error-type :idb-operation
            :reason     reason
            :source     (->source (.-source req))
            :query      query-spec
            :error      (.-error req)}))


(defn- prom-req
  "Promisify a request"
  [req qspec fail-reason]
  (p/create
   (fn [resolve reject]
     (letfn [(error []
               (reject (idb-op-error req qspec fail-reason))
               (cleanup))
             (success []
               (resolve (.-result req))
               (cleanup))
             (cleanup []
               (.removeEventListener req "success" success)
               (.removeEventListener req "error" error))]
       (.addEventListener req "success" success)
       (.addEventListener req "error" error)))))


(defmulti idb-request (fn [[qtype] store res] qtype))

(defmethod idb-request :idb/get
  [[_ key :as query-spec] store]
  (prom-req (.get store key) query-spec :read-fail))

(defmethod idb-request :idb/get-all
  [[_ & qargs :as query-spec] store]
  (prom-req (apply js-invoke store "getAll" qargs) query-spec :read-fail))

(defmethod idb-request :idb/get-key
  [[_ key :as query-spec] store]
  (prom-req (.getKey store key) query-spec :read-fail))

(defmethod idb-request :idb/get-all-keys
  [[_ & qargs :as query-spec] store]
  (prom-req (apply js-invoke store "getAllKeys" qargs) query-spec :read-fail))

(defmethod idb-request :idb/cursor
  [[_ & qargs :as query-spec] store]
  (prom-req (apply js-invoke store "openCursor" qargs) query-spec :cursor-fail))

(defmethod idb-request :idb/key-cursor
  [[_ & qargs :as query-spec] store]
  (prom-req (apply js-invoke store "openKeyCursor" qargs) query-spec :cursor-fail))

(defmethod idb-request :idb/index
  [[_ name query-spec] store]
  (if (contains? #{:idb/count
                   :idb/get
                   :idb/get-all
                   :idb/get-key
                   :idb/get-all-keys
                   :idb/cursor
                   :idb/key-cursor} (first query-spec))
    (idb-request query-spec (.index store name))
    (p/rejected (ex-info "Unsupported operation on index."
                         {:error-type :idb-operation
                          :reason     :unsupported-index-operation
                          :query      query-spec}))))

(defmethod idb-request :idb/count
  [[_ key :as query-spec] store]
  (prom-req (.count store key) query-spec :read-fail))

(defmethod idb-request :idb/put
  [[_ & qargs :as query-spec] store]
  (p/then (prom-req (apply js-invoke store "put" qargs) query-spec :write-fail)
          (fn [_] (first qargs))))

(defmethod idb-request :idb/add
  [[_ & qargs :as query-spec] store]
  (p/then (prom-req (apply js-invoke store "add" qargs) query-spec :write-fail)
          (fn [_] (first qargs))))

(defmethod idb-request :idb/delete
  [[_ key :as query-spec] store]
  (prom-req (.delete store key) query-spec :delete-fail))

(defmethod idb-request :idb/clear
  [[_ :as query-spec] store]
  (prom-req (.clear store) query-spec :delete-fail))

(defmethod idb-request :default
  [[qtype] _]
  (p/rejected (ex-info "Unknown operation."
                       {:error-type :idb-operation
                        :reason     :unknown-operation
                        :operation  qtype})))


(defn- cache-tx-done!
  [tx]
  (if (.has TX_DONE tx)
    nil
    (.set TX_DONE tx (p/create
                      (fn [resolve reject]
                        (letfn [(complete []
                                  (resolve)
                                  (cleanup))
                                (abort []
                                  (reject (ex-info "Transaction aborted."
                                                   {:error-type :idb-transaction
                                                    :reason     :tx-abort
                                                    :error      (.-error tx)}))
                                  (cleanup))
                                (error []
                                  (reject (ex-info "Transaction error."
                                                   {:error-type :idb-transaction
                                                    :reason     :tx-error
                                                    :error      (.-error tx)}))
                                  (cleanup))
                                (cleanup []
                                  (.removeEventListener tx "complete" complete)
                                  (.removeEventListener tx "abort" abort)
                                  (.removeEventListener tx "error" error))]
                          (.addEventListener tx "complete" complete)
                          (.addEventListener tx "abort" abort)
                          (.addEventListener tx "error" error)))))))


(def indexedDB
  (if (exists? js/WorkerGlobalScope)
    js/self.indexedDB
    js/window.indexedDB))


(deftype IndexedDB [db-opts ^:mutable idb]
  proto/Database
  (open-database [this]
    (if idb
      (p/resolved idb)
      (let [req (.open indexedDB (db-opts :name) (db-opts :version 1))]
        (set! (.-onupgradeneeded req) (db-opts :on-upgrade identity))
        (p/create
         (fn [resolve reject]
           (letfn [(error []
                     (reject (ex-info "Cannot open IndexedDB."
                                      {:error-type :idb-error
                                       :reason     :db-open-fail
                                       :error      (.-error req)}))
                     (cleanup))
                   (success []
                     (resolve (set! idb (.-result req)))
                     (cleanup))
                   (cleanup []
                     (.removeEventListener req "success" success)
                     (.removeEventListener req "error" error))]
             (.addEventListener req "success" success)
             (.addEventListener req "error" error)))))))
  (close-database [this]
    (set! idb nil)
    (p/resolved true))
  (create-transaction [this opts]
    (p/then (proto/open-database this)
            (fn []
              (doto
                  (.transaction idb (into-array (opts :stores)) (->mode (opts :mode)))
                cache-tx-done!))))
  (execute-query [this tx [store-name query-spec]]
    (idb-request query-spec (.objectStore tx store-name)))
  (commit-transaction [this tx]
    (or (.get TX_DONE tx)
        (p/resolved nil)))
  (abort-transaction [this tx]
    (.abort tx)
    (or (.get TX_DONE tx)
        (p/rejected nil))))


(defn new-idb
  [opts]
  (IndexedDB. opts nil))


;;; Key range helpers
(defn r<=
  "All keys ≤ x"
  [x]
  (js/IDBKeyRange.upperBound x))

(defn r<
  "All keys < x"
  [x]
  (js/IDBKeyRange.upperBound x true))

(defn r>=
  "All keys ≥ y"
  [y]
  (js/IDBKeyRange.lowerBound y))

(defn r>
  "All keys > y"
  [y]
  (js/IDBKeyRange.lowerBound y true))

(defn r>=<=
  "All keys ≥ x && ≤ y"
  [x y]
  (js/IDBKeyRange.bound x y))

(defn r><=
  "All keys > x && ≤ y"
  [x y]
  (js/IDBKeyRange.bound x y true false))

(defn r>=<
  "All keys ≥ x && < y"
  [x y]
  (js/IDBKeyRange.bound x y false true))

(defn r><
  "All keys > x && < y"
  [x y]
  (js/IDBKeyRange.bound x y true true))

(defn r=
  "All keys = x"
  [x]
  (js/IDBKeyRange.only x))
