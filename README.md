# aye-db

A minimal IndexedDB wrapper for ClojureScript

![star trek scotty](https://i.imgflip.com/3ss1qk.jpg)


## Installation

[![Clojars Project](https://img.shields.io/clojars/v/g7s/aye-db.svg)](https://clojars.org/g7s/aye-db)

To install, add the following to your project `:dependencies`:

    [g7s/aye-db "0.1.0"]


## Usage

**NOTE**: This library uses promises to create a better API for IndexedDB (which uses callbacks).

To start just create a database instance as such:


```clojure
(ns my.app.database
  (:require
   [g7s.aye-db :as idb]))


(def db
  (idb/new-idb {:name    "mydb"
                :version 2
                :on-upgrade
                (fn [e]
                  (let [ndb  (.-result (.-target e))
                        oldv (.-oldVersion e)]
                    (when (< oldv 1)
                      (.createObjectStore ndb "objects"))
                    (when (< oldv 2)
                      (.createObjectStore ndb "other-objects"))))}))
```


One can execute queries against this database using the `with-tx` macro that is of the form

```clojure
(with-tx db exec-fn-symbol & body)
```

where `db` is the database instance that you created, the `exec-fn-symbol` is a symbol that you have
to use inside the `body` to execute queries (it can be anything you like) and represents a function with
a signature:

```clojure
(exec-fn-symbol [object-store [query-type & args]])
```


where `object-store` is the string name of the object store that you are querying against and `query-type`
is a keyword (e.g. `:idb/get`) and `args` are data that are needed by the `query-type`. The mapping of
query type keyword to the native IndexedDB method is:

| query type    | IndexedDB     |
| ------------- |---------------|
| `:idb/get` | [IDBObjectStore.get()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/get)|
| `:idb/get-all` | [IDBObjectStore.getAll()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/getAll)|
| `:idb/get-key`  | [IDBObjectStore.getKey()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/getKey)|
| `:idb/get-all-keys` | [IDBObjectStore.getAllKeys()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/getAllKeys)|
| `:idb/cursor` | [IDBObjectStore.openCursor()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/openCursor)|
| `:idb/key-cursor` | [IDBObjectStore.openKeyCursor()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/openKeyCursor)|
| `:idb/index` | [IDBObjectStore.index()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/index)|
| `:idb/count` | [IDBObjectStore.count()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/count)|
| `:idb/put` | [IDBObjectStore.put()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/put)|
| `:idb/add` | [IDBObjectStore.add()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/add)|
| `:idb/delete` | [IDBObjectStore.delete()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/delete)|
| `:idb/clear` | [IDBObjectStore.clear()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/clear)|

**NOTE**: The `:idb/index` query accepts only two arguments; the name of the index to open and a query to run on the index e.g.

```clojure
(exec-fn-symbol ["users" [:idb/index "by-name" [:idb/get "John"]]])
```

Because some queries accept a [key range](https://developer.mozilla.org/en-US/docs/Web/API/IDBKeyRange) instead of just a key, aye-db exposes functions for constructing key ranges:

| aye-db | IndexedDB constructor | Meaning |
|--------------|-----------------------|---------|
| `(r<= x)` | IDBKeyRange.upperBound(x) | All keys ≤ x |
| `(r< x)` | IDBKeyRange.upperBound(x, true) | All keys < x |
| `(r>= y)` | IDBKeyRange.lowerBound(y) | All keys ≥ y |
| `(r> y)` | IDBKeyRange.lowerBound(y, true) | All keys > y |
| `(r>=<= x y)` | IDBKeyRange.bound(x, y) | All keys ≥ x && ≤ y |
| `(r><= x y)` | IDBKeyRange.bound(x, y, true, false) | All keys > x && ≤ y |
| `(r>=< x y)` | IDBKeyRange.bound(x, y, false, true) | All keys ≥ x && < y |
| `(r>< x y)` | IDBKeyRange.bound(x, y, true, true) | All keys > x && < y |
| `(r= x)` | IDBKeyRange.only(x) | All keys = x |

An example usage of `with-tx` is the following

```clojure
(require '[promesa.core :as p])

(with-tx db exec!
  (p/let [fk   "a/path"
          tk   "another/path"
          file (exec! ["files" [:idb/get fk]])]
    (exec! ["files" [:idb/delete fk]])
    (exec! ["files" [:idb/put file tk]])))
```


## License

Copyright © 2020 Gerasimos

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
