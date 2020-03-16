(ns g7s.aye-db.protocols)


(defprotocol Database
  (open-database [this])
  (close-database [this])
  (create-transaction [this opts])
  (execute-query [this tx query])
  (commit-transaction [this tx])
  (abort-transaction [this tx]))
