(ns com.compass.hbase.schema
  (:use [clojure.contrib.seq-utils :only [find-first]]
	[clojure.contrib.java-utils])
  (:import org.apache.hadoop.hbase.util.Bytes)
  (:require [clojure.stacktrace]
	    [clj-time.core :as time]
	    [clj-serializer.core :as ser]
	    [clojure.contrib.json :as json]))

;;
;; Schema-based translation between HBase byte representations
;; and clojure representations.  Remove visibility into icky
;; HBase Java interface.
;;
;; Simplifying assumptions:
;; - Tables and families are clojure keywords, strings in the DB
;; - Qualifiers can be anything, but default to strings (not keywords)
;; - Values, of course, can be anything and default to strings
;; - Timestamps are Java longs
;; - Exceptions map specific keys to a value type for deser.
;;   this only works for column families where all qualifiers have the same type
;;   because they have to be decoded prior to the lookup

(comment
  (def define-schema test-table [:row-type :long
				 :key-type :string
				 :value-type :json-key]
    :family1 {:key-type :long
	      :value-type :string
	      :exceptions {(long 0) :json
			   (long 1) :double}}
    :family2 {:value-type :json}))

(defrecord hbase-schema [name metadata families])

(defmethod print-method hbase-schema [schema writer]
  (.write writer (format "#<schema %s>" (:name schema))))

(defn make-schema
  "Families is a map of family names to type definitions"
  [name families metadata]
  (hbase-schema. name metadata families))

;;
;; Schema accessors
;; 

(def *row-default* :string)
(def *qualifier-default* :keyword)
(def *value-default* :json)
(def *valid-types* [:bool :int :long :string :symbol :keyword :ser :json :json-key])

(defn check-schema [schema]
  (when (not (and (map? schema) (:metadata schema)))
    (throw (java.lang.Error. (format "Invalid schema: %s" schema)))))

(defn- schema-metadata
  "Get metadata for a schema"
  [schema name]
  (check-schema schema)
  ((:metadata schema) name))

(defn- schema-family
  "Return the family's schema"
  [schema family]
  (check-schema schema)
  ((:families schema) family))

(defn- qualifier-type
  "Return the specified type of the qualifier.  All qualifiers must have the
   same serialization type"
  [schema family]
  (check-schema schema)
  (if-let [type (:key-type (schema-family schema family))]
    type
    (if-let [type (schema-metadata schema :key-type)]
      type
      *qualifier-default*)))
	     
(defn- family-value-type [family qualifier]
  (or (and (:exceptions family) 
	   ((:exceptions family) qualifier))
      (:value-type family)))
   
(defn- value-type
  [schema family qualifier]
  (check-schema schema)
  (or (family-value-type (schema-family schema family) qualifier)
      (schema-metadata schema :value-type)
      *value-default*))

(defn row-type
  [schema]
  (check-schema schema)
  (schema-metadata schema :row-type))

;;
;; Define and cache schemas for convenience
;;

(defonce *schemas* (atom nil))
(defn- put-schema* [orig name schema] (assoc orig name schema))
(defn put-schema [name schema]        (swap! *schemas* put-schema* name schema))
(defn get-schema [name]               (if-let [recs @*schemas*]
					(recs (keyword name))))

(defn- matching-type? [rtype vtype]
  (case rtype
	:string (string? vtype)
	:keyword (keyword? vtype)
	:symbol (symbol? vtype)
	:bool (or (= vtype true) (= vtype false))
	(:int :long) (integer? vtype)
	(:float :double) (float? vtype)
	(:json :json-key :ser :raw) true
	true))

(defn- valid-exception-map? [type emap]
  (cond (nil? emap) true
	(nil? type) 
	(throw (java.lang.Error. "Families with exceptions must specify key-type"))
	(not (every? (partial matching-type? type) (keys emap)))
	(throw (java.lang.Error. "Exception keys must match family key-type"))))

(defn- canonical-family-spec [fams [fam fspec]]
  (assert (or (keyword? fam) (string? fam)))
  (cond (map? fspec)
	(do (assert (every? #{:key-type :value-type :exceptions} (keys fspec)))
	    (valid-exception-map? (:key-type fspec) (:exceptions fspec))
	    (assoc fams fam fspec))
	(and (sequential? fspec) (= (count fspec) 2))
	(assoc fams
	  fam {:key-type (first fspec)
	       :value-type (second fspec)})
	true (throw (java.lang.Error.
		     (str "Unknown family " fspec " for family " fam)))))
  
(defn canonical-families [spec]
  (reduce canonical-family-spec (hash-map) (partition 2 spec)))

(defmacro define-schema
  "A convenience macro for systems to use"
  [table-name [& metadata] & family-defs]
  (let [table-name (as-str table-name)]
    `(put-schema '~(keyword table-name)
		 (make-schema
		  ~(str table-name)
		  ~(canonical-families family-defs)
		  ~(assoc (apply hash-map metadata)
		     :table (keyword table-name))))))

(define-schema :schemas [:row-type :keyword
			 :key-type :string
			 :value-type :json-key])

;;
;; Schema-guided encoding for HBase
;;

(defmulti encode-value
  "Encode clojure values according to schema definition. Reasonable conversions
   are supported for strings (e.g. symbols->strings)"
  (fn [value type] type))

;; Primitives
(defmethod encode-value :keyword [arg type] (Bytes/toBytes (as-str arg)))
(defmethod encode-value :symbol [arg type] (Bytes/toBytes (as-str arg)))
(defmethod encode-value :string [arg type]
	   (assert (or (symbol? arg) (keyword? arg) (string? arg)))
	   (Bytes/toBytes (as-str arg)))
(defmethod encode-value :bool [arg type] (Bytes/toBytes (boolean arg)))
(defmethod encode-value :long [arg type] (Bytes/toBytes (long arg)))
(defmethod encode-value :int [arg type] (Bytes/toBytes (int arg)))
(defmethod encode-value :float [arg type] (Bytes/toBytes (float arg)))
(defmethod encode-value :double [arg type] (Bytes/toBytes (double arg)))
(defmethod encode-value :raw [arg type] arg)

;; Aggregates
(defmethod encode-value :ser [arg type] (ser/serialize arg))
(defmethod encode-value :json [arg type] (Bytes/toBytes (json/json-str arg)))
(defmethod encode-value :json-key [arg type] (Bytes/toBytes (json/json-str arg)))

(defmethod encode-value :default [arg] (assert false))

;;
;; Schema-guided encoding
;;

(defn encode-row [schema row]
  (encode-value row (row-type schema)))

(defn encode-family [schema family]
  (encode-value family :string))

(defn encode-column [schema family column]
  (encode-value column (qualifier-type schema family)))

(defn encode-cell [schema family column value]
  (encode-value value (value-type schema family column)))

;;
;; Schema-guided decoding for HBase
;;

(defmulti decode-value
  "Decode byte sequences according to type specification"
  (fn [data type] type))

;; Primitive types
(defmethod decode-value :string [bytes type] (Bytes/toString bytes))
(defmethod decode-value :symbol [bytes type] (intern (Bytes/toString bytes)))
(defmethod decode-value :keyword [bytes type] (keyword (Bytes/toString bytes)))
(defmethod decode-value :long [bytes type] (Bytes/toLong bytes))
(defmethod decode-value :int [bytes type] (Bytes/toInt bytes))
(defmethod decode-value :bool [bytes type] (Bytes/toBoolean bytes))
(defmethod decode-value :float [bytes type] (Bytes/toFloat bytes))
(defmethod decode-value :double [bytes type] (Bytes/toDouble bytes))
(defmethod decode-value :raw [bytes type] bytes)

;; Aggregate data methods
(defmethod decode-value :ser [bytes type] (ser/deserialize bytes nil))
(defmethod decode-value :json [bytes type] (json/read-json (Bytes/toString bytes) nil))
(defmethod decode-value :json-key [bytes type] (json/read-json (Bytes/toString bytes) true))

(defmacro with-robust-decode [[type result] & body]
  `(try
     ~@body
     (catch java.lang.Throwable e#
       (clojure.stacktrace/print-throwable e#)
       (println "Can't decode " ~type " for row " (.getRow ~result))
       nil)))

(defn decode-row [schema result]
  (with-robust-decode [:row result]
    (decode-value (.getRow result) (row-type schema))))

(defn decode-all
  "Given an HBase Result object, decode all the versions such that for each
   family and column there is a map of timestamp and values for historical versions"
  [schema result]
  (assert schema)
  (if (or (not result) (.isEmpty result))
    (do (println "Empty results") nil)
    [(with-robust-decode [:row result]
       (decode-row schema result))
     (loop [kvs (.raw result)
	    kv-map {}]
       (if-let [kv (first kvs)]
	 (let [family (with-robust-decode [:keyword result]
			(decode-value (.getFamily kv) :keyword))
	       qualifier (with-robust-decode [:qualifier result]
			   (decode-value (.getQualifier kv)
					 (qualifier-type schema family)))
	       timestamp (.getTimestamp kv)
	       value (let [value (.getValue kv)]
		       (if (> (count value) 0)
			 (with-robust-decode [:keyword result]
			   (decode-value (.getValue kv)
					 (value-type schema family qualifier)))
			 nil))]
	   (recur (next kvs)
		  (assoc-in kv-map [family qualifier timestamp] value)))
	 kv-map))]))
			      
(defn decode-latest
  "Given an HBase Result object, decode the latest versions of all the
   available columns"
  [schema result]
  (assert schema)
  (if (or (not result) (.isEmpty result))
    nil
    [(with-robust-decode [:row result]
       (decode-row schema result))
     (loop [remaining-kvs (seq (.raw result))
	    keys #{}]
       (if-let [kv (first remaining-kvs)]
	 (let [family    (.getFamily kv)
	       qualifier (.getQualifier kv)]
	   (recur (next remaining-kvs)
		  (conj keys [family qualifier])))
	 ;; At this point, we have a duplicate-less list of [f q] keys in keys.
	 ;; Go back through, pulling the latest values for these keys.
	 (loop [remaining-keys keys
		kv-map {}]
	   (if-let [[family qualifier] (first remaining-keys)]
	     (let [keyfam (decode-value family :keyword)
		   qual (with-robust-decode [:qualifier result]
			  (decode-value qualifier (qualifier-type schema keyfam)))]
	       (recur (next remaining-keys)
		      (assoc-in kv-map [keyfam qual]
				(let [value (.getValue result family qualifier)]
				  (if (> (count value) 0)
				    (with-robust-decode [:cell result]
				      (decode-value value (value-type schema keyfam qual)))
				    nil)))))
	     kv-map))))]))
