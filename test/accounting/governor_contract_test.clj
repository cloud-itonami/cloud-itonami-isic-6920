(ns accounting.governor-contract-test
  "The governor contract as executable tests -- the accounting/
  auditing analog of `cloud-itonami-isic-6512`'s `casualty.governor-
  contract-test`. The single invariant under test:

    Ledger-LLM never issues an audit opinion or submits a tax filing
    the Audit Independence Governor would reject, `:audit-opinion/
    issue`/`:tax-filing/submit` NEVER auto-commit at any phase,
    `:engagement/intake` (no capital risk) MAY auto-commit when clean,
    and every decision (commit OR hold) leaves exactly one ledger
    fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [accounting.store :as store]
            [accounting.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :accountant :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :engagement/intake :subject "engagement-1"
                   :patch {:id "engagement-1" :client "Sakura Manufacturing K.K."}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Manufacturing K.K." (:client (store/engagement db "engagement-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "engagement-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "engagement-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "engagement-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "engagement-1")) "no assessment written"))))

(deftest independence-conflict-is-held-and-unoverridable
  (testing "an independence conflict on an engagement -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :independence/screen :subject "engagement-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:independence-violation} (-> (store/ledger db) first :basis)))
      (is (nil? (store/independence-of db "engagement-5")) "no clearance written"))))

(deftest audit-opinion-without-assessment-is-held
  (testing "audit-opinion/issue before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t5" {:op :audit-opinion/issue :subject "engagement-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest wrong-engagement-type-for-opinion-is-held
  (testing "issuing an audit opinion against a tax-filing-only engagement -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "engagement-6")
          res (exec-op actor "t6" {:op :audit-opinion/issue :subject "engagement-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:wrong-engagement-type} (-> (store/ledger db) last :basis))))))

(deftest wrong-engagement-type-for-filing-is-held
  (testing "submitting a tax filing against an audit-only engagement -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "engagement-1")
          res (exec-op actor "t7" {:op :tax-filing/submit :subject "engagement-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:wrong-engagement-type} (-> (store/ledger db) last :basis))))))

(deftest trial-balance-out-of-balance-is-held
  (testing "an engagement whose trial balance does not satisfy assets = liabilities + equity -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "engagement-3")
          res (exec-op actor "t8" {:op :audit-opinion/issue :subject "engagement-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:trial-balance-out-of-balance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/opinion-history db))))))

(deftest audit-opinion-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, balanced engagement still ALWAYS interrupts for human approval -- actuation/issue-opinion is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "engagement-1")
          r1 (exec-op actor "t9" {:op :audit-opinion/issue :subject "engagement-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, audit-opinion record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:opinion-issued? (store/engagement db "engagement-1"))))
          (is (= 1 (count (store/opinion-history db))) "one draft opinion record")))))
  (testing "reject -> hold, nothing issued"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "engagement-1")
          _ (exec-op actor "t10" {:op :audit-opinion/issue :subject "engagement-1"} operator)
          r2 (g/run* actor {:approval {:status :rejected :by "op-1"}}
                     {:thread-id "t10" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (empty? (store/opinion-history db)) "nothing issued on reject"))))

(deftest audit-opinion-double-issuance-is-held
  (testing "issuing a second opinion for the same engagement -> HOLD, even though the figures match cleanly"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "engagement-1")
          _ (exec-op actor "t11a" {:op :audit-opinion/issue :subject "engagement-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :audit-opinion/issue :subject "engagement-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-issued} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/opinion-history db))) "still only the one earlier opinion"))))

(deftest tax-filing-always-escalates-then-human-decides
  (testing "a clean, fully-assessed tax-filing engagement still ALWAYS interrupts for human approval -- actuation/submit-filing is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t12pre" "engagement-4")
          r1 (exec-op actor "t12" {:op :tax-filing/submit :subject "engagement-4"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, tax-filing record drafted"
        (let [r2 (approve! actor "t12")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:filing-submitted? (store/engagement db "engagement-4"))))
          (is (= 1 (count (store/filing-history db))) "one draft filing record"))))))

(deftest tax-filing-double-filing-is-held
  (testing "submitting a second filing for the same engagement -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t13pre" "engagement-4")
          _ (exec-op actor "t13a" {:op :tax-filing/submit :subject "engagement-4"} operator)
          _ (approve! actor "t13a")
          res (exec-op actor "t13" {:op :tax-filing/submit :subject "engagement-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-filed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/filing-history db))) "still only the one earlier filing"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :engagement/intake :subject "engagement-1"
                          :patch {:id "engagement-1" :client "Sakura Manufacturing K.K."}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "engagement-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
