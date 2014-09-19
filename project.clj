(defproject com.compasslabs/clojure-hbase-schemas "0.92.2.0"
  :description "A convenient Clojure interface to HBase."
  :license "See LICENSE file"
  :url "http://github.com/compasslabs/clojure-hbase-schemas"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.apache.hadoop/hadoop-core "1.0.4"]
                 [org.apache.hbase/hbase "0.92.1"]
                 [org.apache.zookeeper/zookeeper "3.4.6"]
                 [clj-time "0.7.0"]
                 [clj-serializer "0.1.3"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [commons-logging/commons-logging "1.1.3"]]
  )
