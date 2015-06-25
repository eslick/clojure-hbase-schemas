(defproject com.compasslabs/clojure-hbase-schemas "1.0.1.1"
  :description "A convenient Clojure interface to HBase."
  :license "See LICENSE file"
  :url "http://github.com/compasslabs/clojure-hbase-schemas"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.apache.hbase/hbase-client "1.0.1.1"]
                 [org.apache.hadoop/hadoop-hdfs "2.4.1"]
                 [clj-time "0.7.0"]
                 [clj-serializer "0.1.3"]
                 [org.clojure/data.json "0.2.6"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 ]
  )
