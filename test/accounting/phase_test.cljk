(ns accounting.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:audit-opinion/issue`/`:tax-filing/submit` must NEVER be
  a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [accounting.phase :as phase]))

(deftest audit-opinion-issue-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real audit opinion"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :audit-opinion/issue))
          (str "phase " n " must not auto-commit :audit-opinion/issue")))))

(deftest tax-filing-submit-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-submits a real tax filing"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :tax-filing/submit))
          (str "phase " n " must not auto-commit :tax-filing/submit")))))

(deftest independence-screen-never-auto-at-any-phase
  (testing "screening moves no capital, but is still never auto-eligible, matching every sibling KYC/conflict screen"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :independence/screen))
          (str "phase " n " must not auto-commit :independence/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":engagement/intake moves no capital -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:engagement/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :engagement/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :audit-opinion/issue} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :tax-filing/submit} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :engagement/intake} :commit)))))
