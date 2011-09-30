(ns com.compass.hbase.admin-utils
  (:refer-clojure :rename {get map-get})
  (:use clojure.contrib.def
        [clojure.contrib.string :only [as-str]])
  (:import [org.apache.hadoop.hbase.util Bytes]))

;; Utility function

(defn partition-query
  "Given a query sequence and a command argnum map (each keyword in map
   mapped to how many arguments that item expects), this function returns
   a sequence of sequences; each sub-sequence is just a single command,
   command keyword followed by args."
  [query cmd-argnum-map]
  (loop [result []
	 remaining-commands query]
    (let [kw (first remaining-commands)]
      (if (nil? kw)
	result
	(let [[a-cmd rest-cmds] (split-at (inc (map-get cmd-argnum-map kw 1))
					  remaining-commands)]
	  (recur (conj result a-cmd) rest-cmds))))))

;; Some default conversions

(def ^{:doc "We need a type object for extend-protocol"}
  byte-array-type
  (Class/forName "[B"))

(defprotocol PBytesEncoder
  (to-bytes [value]))

(extend-protocol PBytesEncoder

  byte-array-type
  (to-bytes [value] value)

  clojure.lang.Keyword
  (to-bytes [value]
    (-> value as-str Bytes/toBytes))

  clojure.lang.Symbol
  (to-bytes [value]
    (-> value as-str Bytes/toBytes))

  clojure.lang.IPersistentList
  (to-bytes [value]
    (-> (binding [*print-dup* false] (pr-str value))
        Bytes/toBytes))

  clojure.lang.IPersistentVector
  (to-bytes [value]
    (-> (binding [*print-dup* false] (pr-str value))
        Bytes/toBytes))

  clojure.lang.IPersistentMap
  (to-bytes [value]
    (-> (binding [*print-dup* false] (pr-str value))
        Bytes/toBytes))

  ;; default
  java.lang.Object
  (to-bytes [value]
    (Bytes/toBytes value)))

