(ns accounting.corporate-intel-test
  "Proves the value `accounting.corporate-intel` actually adds: a client
  engagement that is clean on every LOCAL field (no
  `:independence-conflict?`, balanced trial balance) but whose CLIENT
  COMPANY is directly sanctions-flagged in `cloud-itonami-isic-8291`'s
  own demo data no longer silently clears -- something 6920's local-
  only independence check alone would have missed entirely (see
  `engagement-7` in `accounting.store/demo-data`, sharing its `:client`
  name byte-exact with 8291's sanctions-flagged demo company `co-200`).

  Note: `:independence/screen` NEVER auto-commits at any phase (see
  `accounting.phase`) -- every scenario below that reaches `:commit`
  does so via an explicit approve, same as every other
  `:independence/screen` test in `governor_contract_test.clj`. Only a
  HARD violation (a local `:independence-conflict?`, or a stubbed
  definitive corporate-intel sanctions flag) settles immediately with
  no interrupt at all -- 8291's OWN real hits always escalate for
  8291's own human review first (no shortcut, no peeking behind its
  DisclosureGovernor), so the end-to-end proof here is 'no longer
  silently clears', not 'now hard-holds'.

  EMPIRICALLY VERIFIED (not just predicted): when the REAL 8291 actor
  escalates a `:disclosure/query` hit, ITS OWN audit-trail reason is
  `:high-stakes` (8291's `:stake :sanctions-flag` -> DisclosureGovernor
  `:high-stakes?` -> `dossier.operation`'s decide-node reason, since
  `:disclosure/query` is one of 8291's own read-ops so its phase gate
  contributes no override reason of its own). 6920's OWN audit trail,
  one layer up, reasons independently off ITS OWN governor verdict
  (confidence 0.5 from the `:pending-human-review?` branch below,
  `:stake nil` since this actor's `:conflict`/`:incomplete` distinction
  is not itself a `high-stakes` kind here) and lands on
  `:low-confidence` -- see `corporate-intel-catches-the-hit-local-checks-miss`."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [accounting.store :as store]
            [accounting.operation :as op]
            [accounting.ledgerllm :as ledgerllm]
            [accounting.corporate-intel :as ci]))

(def operator {:actor-id "op-1" :actor-role :accountant :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- wired-actor
  "A fresh accounting Store + OperationActor with `accounting.corporate-
  intel/screen-company` wired into the mock advisor -- exercises the
  REAL (unmocked) 8291 actor end-to-end."
  []
  (let [db (store/seed-db)]
    [db (op/build db {:advisor (ledgerllm/mock-advisor {:corporate-intel-screen ci/screen-company})})]))

(deftest local-checks-alone-would-miss-the-8291-flagged-client
  (testing "sanity: without the integration wired in, engagement-7 passes the local check and clears"
    (let [db (store/seed-db)
          actor (op/build db)                          ; NO corporate-intel wired in
          res (exec-op actor "sanity" {:op :independence/screen :subject "engagement-7"} operator)]
      (is (= :interrupted (:status res)) ":independence/screen always escalates for approval, clean or not")
      (approve! actor "sanity")
      (is (= :clear (:verdict (store/independence-of db "engagement-7")))
          "without the integration, engagement-7 screens :clear -- this is the gap being closed"))))

(deftest corporate-intel-catches-the-hit-local-checks-miss
  (testing "with the REAL (unmocked) 8291 actor wired in, engagement-7 no longer silently clears"
    (let [[db actor] (wired-actor)
          res (exec-op actor "t1" {:op :independence/screen :subject "engagement-7"} operator)]
      (is (= :interrupted (:status res))
          "8291 itself escalates a real hit for ITS OWN human review first -- 6920 never
           peeks behind that gate, so this reads as :incomplete + low confidence one layer up")
      (is (= :low-confidence (-> res :state :audit last :reason))
          "empirically verified: 6920's OWN governor reasons off confidence 0.5 / :stake nil here,
           independent of 8291's own internal :high-stakes reason for ITS escalation")
      (approve! actor "t1")
      (is (not= :clear (:verdict (store/independence-of db "engagement-7")))
          "critically: it never becomes :clear, unlike the unwired sanity case above")
      (is (= :incomplete (:verdict (store/independence-of db "engagement-7")))))))

(deftest corporate-intel-definitive-sanctions-flag-hard-holds
  (testing "screen-independence's sanctions-flag branch itself is a HARD, un-overridable hold -- proven
            directly with a stub (a real 8291 hit always escalates for 8291's own human first, so this
            branch is only reachable end-to-end after that human confirms; unit-testing it here keeps
            the assertion deterministic and isolated from 8291's real timing)"
    (let [db (store/seed-db)
          definitive-hit (fn [_client] {:flags {:sanctions? true}})
          actor (op/build db {:advisor (ledgerllm/mock-advisor {:corporate-intel-screen definitive-hit})})
          res (exec-op actor "t2" {:op :independence/screen :subject "engagement-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:independence-violation} (-> (store/ledger db) first :basis)))
      (is (nil? (store/independence-of db "engagement-7")) "no independence clearance written"))))

(deftest corporate-intel-held-screen-degrades-to-incomplete-not-clear
  (testing "if 6920's own tenant contract with 8291 is missing/misconfigured, 8291 itself holds the
            query -- 6920 must treat that as inconclusive (escalate), never as clear"
    (let [db (store/seed-db)
          broken-screen (fn [_client] {:held? true :reason [:licensed-disclosure]})
          actor (op/build db {:advisor (ledgerllm/mock-advisor {:corporate-intel-screen broken-screen})})
          res (exec-op actor "t3" {:op :independence/screen :subject "engagement-7"} operator)]
      (is (= :interrupted (:status res)) "low confidence (:incomplete) -> escalate, never silently :clear")
      (is (nil? (store/independence-of db "engagement-7")))
      (approve! actor "t3")
      (is (= :incomplete (:verdict (store/independence-of db "engagement-7")))))))

(deftest corporate-intel-clean-client-still-clears
  (testing "an engagement whose client has NO 8291 data at all still screens :clear normally --
            additive, not stricter-by-default (a confident not-found is not treated as a hit)"
    (let [[db actor] (wired-actor)
          res (exec-op actor "t4" {:op :independence/screen :subject "engagement-1"} operator)]
      (is (= :interrupted (:status res)))
      (approve! actor "t4")
      (is (= :clear (:verdict (store/independence-of db "engagement-1")))))))

(deftest corporate-intel-local-conflict-short-circuits-before-8291-is-consulted
  (testing "a local :independence-conflict? decides the verdict first -- 8291 is never even queried"
    (let [[db actor] (wired-actor)
          res (exec-op actor "t5" {:op :independence/screen :subject "engagement-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:independence-violation} (-> (store/ledger db) first :basis))))))
