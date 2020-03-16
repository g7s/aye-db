# aye-db

A minimal IndexedDB wrapper for ClojureScript

![aye db - star trek scotty](https://imgflip.com/i/3ss1qk)


## Installation

To install, add the following to your project `:dependencies`:

    [g7s/aye-db "0.1.0"]


## Usage

**NOTE**: This library uses promises to create a better API for IndexedDB (which uses callbacks).

To start just create a database instance as such:


```clojure
(ns my.app.database
  (:require
   [g7s.aye-db :as idb]
   [promesa.core :as p]))


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

```
| query type    | IndexedDB     |
| ------------- |---------------|
| **:idb/get**  | [IDBObjectStore.get()](https://developer.mozilla.org/en-US/docs/Web/API/IDBObjectStore/get)|
```

An example usage of `with-tx` is the following

```clojure
(with-tx this exec!
  (p/let [fk   "a/path"
          tk   "another/path"
          file (exec! ["files" [:idb/get fk]])]
    (p/do!
      (exec! ["files" [:idb/delete fk]])
      (exec! ["files" [:idb/put file tk]]))))
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
