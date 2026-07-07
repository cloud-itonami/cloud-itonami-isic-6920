(ns accounting.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [accounting.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Manufacturing K.K." (:client (store/engagement s "engagement-1"))))
      (is (= "JPN" (:jurisdiction (store/engagement s "engagement-1"))))
      (is (= :audit (:engagement-type (store/engagement s "engagement-1"))))
      (is (= :tax-filing (:engagement-type (store/engagement s "engagement-4"))))
      (is (false? (:independence-conflict? (store/engagement s "engagement-1"))))
      (is (true? (:independence-conflict? (store/engagement s "engagement-5"))))
      (is (= ["engagement-1" "engagement-2" "engagement-3" "engagement-4" "engagement-5" "engagement-6"]
             (mapv :id (store/all-engagements s))))
      (is (nil? (store/independence-of s "engagement-1")))
      (is (nil? (store/assessment-of s "engagement-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/opinion-history s)))
      (is (= [] (store/filing-history s)))
      (is (zero? (store/next-sequence s "JPN")))
      (is (zero? (store/filing-sequence s "JPN")))
      (is (false? (store/opinion-already-issued? s "engagement-1")))
      (is (false? (store/filing-already-submitted? s "engagement-4"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :engagement/upsert
                                 :value {:id "engagement-1" :client "Sakura Manufacturing K.K."}})
        (is (= "Sakura Manufacturing K.K." (:client (store/engagement s "engagement-1"))))
        (is (= :audit (:engagement-type (store/engagement s "engagement-1"))) "engagement-type preserved"))
      (testing "assessment / independence payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["engagement-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "engagement-1")))
        (store/commit-record! s {:effect :independence/set :path ["engagement-1"]
                                 :payload {:engagement-id "engagement-1" :verdict :clear}})
        (is (= {:engagement-id "engagement-1" :verdict :clear} (store/independence-of s "engagement-1"))))
      (testing "audit-opinion issuance drafts an opinion record and advances the sequence"
        (store/commit-record! s {:effect :engagement/mark-opinion-issued :path ["engagement-1"]})
        (is (= "JPN-OPIN-000000" (get (first (store/opinion-history s)) "record_id")))
        (is (= "audit-opinion-draft" (get (first (store/opinion-history s)) "kind")))
        (is (true? (:opinion-issued? (store/engagement s "engagement-1"))))
        (is (= 1 (count (store/opinion-history s))))
        (is (= 1 (store/next-sequence s "JPN")))
        (is (true? (store/opinion-already-issued? s "engagement-1")))
        (is (false? (store/opinion-already-issued? s "engagement-2"))))
      (testing "tax-filing submission drafts a filing record and advances the filing sequence"
        (store/commit-record! s {:effect :engagement/mark-filing-submitted :path ["engagement-4"]})
        (is (= "JPN-FILE-000000" (get (first (store/filing-history s)) "record_id")))
        (is (= "tax-filing-draft" (get (first (store/filing-history s)) "kind")))
        (is (true? (:filing-submitted? (store/engagement s "engagement-4"))))
        (is (= 1 (count (store/filing-history s))))
        (is (= 1 (store/filing-sequence s "JPN")))
        (is (true? (store/filing-already-submitted? s "engagement-4")))
        (is (false? (store/filing-already-submitted? s "engagement-6"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/engagement s "nope")))
    (is (= [] (store/all-engagements s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/opinion-history s)))
    (is (= [] (store/filing-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (is (zero? (store/filing-sequence s "JPN")))
    (store/with-engagements s {"x" {:id "x" :client "c" :engagement-type :audit
                                    :independence-conflict? false :assets 100 :liabilities 60 :equity 40
                                    :jurisdiction "JPN" :status :active}})
    (is (= "c" (:client (store/engagement s "x"))))))
