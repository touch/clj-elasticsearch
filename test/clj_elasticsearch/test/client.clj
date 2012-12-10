(ns clj-elasticsearch.test.client
  (:use [clj-elasticsearch.client] :reload-all)
  (:use [clojure.test]
        [clojure.java.io]
        [cheshire.core]))

(defn delete-dir
  [path]
  (let [d (file path)]
    (if (.isDirectory d)
      (do
        (doseq [f (seq (.listFiles d))]
          (delete-dir f)
          (.delete f))
        (.delete d))
      (.delete d))))

(defn es-fixture
  [f]
  (let [node (make-node {:local-mode true :client-mode false})]
    (with-node-client {:local-mode true}
      (f))
    (.close node)
    (delete-dir "data")))

(use-fixtures :each es-fixture)

(def match-all {"query" {"query_string" {"query" "*:*"}}})

(def match-all-string
  (generate-string match-all))

(deftest utils
  (is (=
       (collapse-tree {:discovery {:zen {:ping {:unicast {:hosts ["a" "b"]}
                                                :multicast {:enabled false}}}}})
       {"discovery.zen.ping.multicast.enabled" false
        "discovery.zen.ping.unicast.hosts" ["a" "b"]})))

(deftest es-client
  (is (false? (:exists (exists-index {:indices ["test"]}))))
  (is (:id (index-doc {:index "test" :type "tyu"
                       :source (build-document {:field1 ["toto" "tutu"] :field2 42 :field3 {:tyu {:foo "bar"}}})
                       :id "mid"})))
  (is (> (:successful-shards (refresh-index {:indices ["test"]})) 0))
  (is (true? (:exists (exists-index {:indices ["test"]}))))
  (is (= 1 (:count (count-docs {:indices ["test"]}))))
  (let [status (index-status {:indices ["test"]})]
    (is (=  1 (get-in status [:indices :test :docs :num_docs]))))
  (let [d (get-doc {:index "test" :type "tyu" :id "mid" :fields ["field1" "field2" "field3"]})]
    (is (nil? (:_source d)))
    (is (= {"tyu" {"foo" "bar"}} (get-in d [:fields "field3"]))))
  (let [d (get-doc {:index "test" :type "tyu" :id "mid"})]
    (is (nil? (:fields d)))
    (is (= {"tyu" {"foo" "bar"}} (get-in d [:_source "field3"])))
    (is (= ["toto" "tutu"] (get-in d [:_source "field1"]))))
  (is (get-in (first
               (get-in (search {:indices ["test"] :types ["tyu"] :extra-source match-all})
                       [:hits :hits]))
              [:_source :field1])
      ["toto" "tutu"])
  (is (get-in (first
               (get-in (search {:indices ["test"] :search-type :query-then-fetch :types ["tyu"] :extra-source match-all-string})
                       [:hits :hits]))
              [:_source :field1])
      ["toto" "tutu"]))
