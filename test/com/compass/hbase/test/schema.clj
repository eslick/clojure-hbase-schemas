(ns com.compass.hbase.test.schema
  (:refer-clojure :exclude [get flush])
  (:use clojure.test
        [com.compass.hbase client schema admin])

  (:import [org.apache.hadoop.hbase.util Bytes]))

(def test-tbl-name "com-compass-hbase-test-schema")
(def test-tbl-kw (keyword test-tbl-name))

(define-schema :com-compass-hbase-test-schema
  [:defaults [:string :ser]
   :row-type :long]
  :family1 {:defaults [:long :string]
            :exceptions {(long 0) :json
                         (long 1) :double}}
  :family2 {:defaults [:long :json]}

  :family3 {:defaults [:string :string]})

(defn setup-tbl []
  (create-table (table-descriptor test-tbl-name))
  (add-column-family test-tbl-name (column-descriptor "family1"))
  (add-column-family test-tbl-name (column-descriptor "family2"))
  (add-column-family test-tbl-name (column-descriptor "family3")))

(defn remove-tbl []
  (disable-table test-tbl-name)
  (delete-table test-tbl-name))

(use-fixtures
 ;; make sure there s no leftover from last run
 :once (fn [f]
         (remove-tbl))
 :each (fn [f]
         (setup-tbl)
         (f)
         (remove-tbl)))


(deftest put-test
  (put test-tbl-kw 1 {:family1 {(long 1) 3}})
  (is (= [1 {:family1 {(long 11) "s:123", 1 3.0}}])))


;; (deftest get-test


;;   )


