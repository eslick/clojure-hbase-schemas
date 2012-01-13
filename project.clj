(defproject com.compasslabs/clojure-hbase-schemas "0.90.4.4"
  :description "A convenient Clojure interface to HBase."
  :license "See LICENSE file"
  :url "http://github.com/compasslabs/clojure-hbase-schemas"
  :dependencies [[org.clojure/clojure "1.3.0"]
		 [org.apache.hadoop/hadoop-core "0.20.2-dev"]
		 [org.apache.hbase/hbase "0.90.4"]
		 [org.apache.zookeeper/zookeeper "3.3.2-CDH3B4"]
		 [clj-time "0.3.2"]
		 [clj-serializer "0.1.1"]
		 [log4j/log4j "1.2.15" :exclusions [javax.mail/mail
						    javax.jms/jms
						    com.sun.jdmk/jmxtools
						    com.sun.jmx/jmxri]]
		 [commons-logging/commons-logging "1.0.4"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.3.0-SNAPSHOT"]
		     [lein-clojars "0.7.0"]])
