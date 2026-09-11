(ns accounting.registry-test
  (:require [clojure.test :refer [deftest is]]
            [accounting.registry :as r]))

;; ----------------------------- trial-balance-difference -----------------------------

(deftest trial-balance-difference-is-zero-when-balanced
  (is (= 0.0 (r/trial-balance-difference {:assets 10000000 :liabilities 6000000 :equity 4000000}))))

(deftest trial-balance-difference-is-nonzero-when-unbalanced
  (is (= 1000000.0 (r/trial-balance-difference {:assets 10000000 :liabilities 6000000 :equity 3000000}))))

;; ----------------------------- register-audit-opinion -----------------------------

(deftest audit-opinion-is-a-draft-not-a-real-opinion
  (let [result (r/register-audit-opinion "engagement-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest audit-opinion-assigns-opinion-number
  (let [result (r/register-audit-opinion "engagement-1" "JPN" 7)]
    (is (= (get result "opinion_number") "JPN-OPIN-000007"))
    (is (= (get-in result ["record" "engagement_id"]) "engagement-1"))
    (is (= (get-in result ["record" "kind"]) "audit-opinion-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest audit-opinion-validation-rules
  (is (thrown? Exception (r/register-audit-opinion "" "JPN" 0)))
  (is (thrown? Exception (r/register-audit-opinion "engagement-1" "" 0)))
  (is (thrown? Exception (r/register-audit-opinion "engagement-1" "JPN" -1))))

(deftest opinion-history-is-append-only
  (let [o1 (r/register-audit-opinion "engagement-1" "JPN" 0)
        hist (r/append [] o1)
        o2 (r/register-audit-opinion "engagement-2" "JPN" 1)
        hist2 (r/append hist o2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-OPIN-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-OPIN-000001" (get-in hist2 [1 "record_id"])))))

;; ----------------------------- register-tax-filing -----------------------------

(deftest tax-filing-is-a-draft-not-a-real-filing
  (let [result (r/register-tax-filing "engagement-4" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest tax-filing-assigns-filing-number
  (let [result (r/register-tax-filing "engagement-4" "JPN" 7)]
    (is (= (get result "filing_number") "JPN-FILE-000007"))
    (is (= (get-in result ["record" "engagement_id"]) "engagement-4"))
    (is (= (get-in result ["record" "kind"]) "tax-filing-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest tax-filing-validation-rules
  (is (thrown? Exception (r/register-tax-filing "" "JPN" 0)))
  (is (thrown? Exception (r/register-tax-filing "engagement-4" "" 0)))
  (is (thrown? Exception (r/register-tax-filing "engagement-4" "JPN" -1))))

(deftest filing-history-is-append-only
  (let [f1 (r/register-tax-filing "engagement-4" "JPN" 0)
        hist (r/append [] f1)
        f2 (r/register-tax-filing "engagement-6" "JPN" 1)
        hist2 (r/append hist f2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-FILE-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-FILE-000001" (get-in hist2 [1 "record_id"])))))
