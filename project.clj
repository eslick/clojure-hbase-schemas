(defproject com.compasslabs/clojure-hbase-schemas "0.92.1.0"
  :description "A convenient Clojure interface to HBase."
  :license "See LICENSE file"
  :url "http://github.com/compasslabs/clojure-hbase-schemas"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.apache.hadoop/hadoop-core "1.0.2"]
                 [org.apache.hbase/hbase "0.92.1"]
                 [org.apache.zookeeper/zookeeper "3.4.3"]
                 [clj-time "0.4.0"]
                 [clj-serializer "0.1.3"]
                 [log4j/log4j "1.2.15" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [commons-logging/commons-logging "1.0.4"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.4.2"]
                     [lein-clojars "0.7.0"]])
