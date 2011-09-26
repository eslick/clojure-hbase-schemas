(ns com.compass.hbase.test.schema
  (:refer-clojure :exclude [get flush])
  (:use clojure.test
        [com.compass.hbase client schema admin])

  (:import [org.apache.hadoop.hbase.util Bytes]))

(def test-tbl-name "com-compass-hbase-test-schema")
(def test-tbl-kw (keyword test-tbl-name))

(def test-id (atom 0))

(define-schema :com-compass-hbase-test-schema
  [:defaults [:string :string]
   :row-type :long]
  :family1 {:defaults [:long :string]
            :exceptions {(long 0) :int
                         (long 1) :double}}
  :family2 {:defaults [:int :long]}

  :family3 {:defaults [:string :long]}
  :family4 {})

(defn setup-tbl []
  (create-table (table-descriptor test-tbl-name))
  (disable-table test-tbl-name)
  (add-column-family test-tbl-name (column-descriptor "family1"))
  (add-column-family test-tbl-name (column-descriptor "family2"))
  (add-column-family test-tbl-name (column-descriptor "family3"))
  (add-column-family test-tbl-name (column-descriptor "family4"))
  (enable-table test-tbl-name))

(defn remove-tbl []
  (disable-table test-tbl-name)
  (delete-table test-tbl-name))

(use-fixtures
 ;; make sure there s no leftover from last run
 :once (fn [f]
         (try
           (setup-tbl)
           (catch Exception _
             nil)
           (finally
            (f)
            (remove-tbl)))))

(use-fixtures :each
              (fn [f]
                (swap! test-id inc)
                (f)))


(deftest put-test-defaults-types
  (let [test-data {:family4 {"key" "value"}}]
    (put test-tbl-kw @test-id test-data)
    (is (= (get test-tbl-kw @test-id)
           [@test-id test-data]))))

(deftest put-test-family1-type
  (let [test-data {:family1 {(long 100) "pwet"}}]
    (put test-tbl-kw @test-id test-data)
    (is (= (get test-tbl-kw @test-id)
           [@test-id test-data]))))

(deftest put-test-family3-type
  (let [test-data {:family3 {"pwet" 1}}]
    (put test-tbl-kw @test-id test-data)
    (is (= (get test-tbl-kw @test-id)
           [@test-id test-data]))))

(deftest put-test-family1-exception-type1
  (let [test-data {:family1 {(long 0) (int 1)}}]
    (put test-tbl-kw @test-id test-data)
    (is (= (get test-tbl-kw @test-id)
           [@test-id test-data]))))

(deftest put-test-family1-exception-type2
  (let [test-data {:family1 {(long 1) 3.0}}]
    (put test-tbl-kw @test-id test-data)
    (is (= (get test-tbl-kw @test-id)
           [@test-id test-data]))))



;; (deftest get-test


;;   )


