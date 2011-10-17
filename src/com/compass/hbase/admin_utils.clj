(ns com.compass.hbase.admin-utils
  (:refer-clojure :rename {get map-get})
  (:use clojure.contrib.def
	[clojure.contrib.java-utils])
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

(defmulti to-bytes-impl
  "Converts its argument into an array of bytes. By default, uses HBase's
   Bytes/toBytes and does nothing to byte arrays. Since it is a multimethod
   you can redefine it to create your own serialization routines for new types."
  class)
(defmethod to-bytes-impl (Class/forName "[B")
  [arg]
  arg)
(defmethod to-bytes-impl clojure.lang.Keyword
  [arg]
  (Bytes/toBytes (as-str arg)))
(defmethod to-bytes-impl clojure.lang.Symbol
  [arg]
  (Bytes/toBytes (as-str arg)))
(defmethod to-bytes-impl clojure.lang.IPersistentList
  [arg]
  (Bytes/toBytes (binding [*print-dup* false] (pr-str arg))))
(defmethod to-bytes-impl clojure.lang.IPersistentVector
  [arg]
  (Bytes/toBytes (binding [*print-dup* false] (pr-str arg))))
(defmethod to-bytes-impl clojure.lang.IPersistentMap
  [arg]
  (Bytes/toBytes (binding [*print-dup* false] (pr-str arg))))
(defmethod to-bytes-impl :default
  [arg]
  (Bytes/toBytes arg))

(defn to-bytes
  "Converts its argument to an array of bytes using the to-bytes-impl
   multimethod. We can't type hint a multimethod, so we type hint this
   shell function and calls all over this module don't need reflection."
  {:tag (Class/forName "[B")}
  [arg]
  (to-bytes-impl arg))
