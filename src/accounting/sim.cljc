(ns accounting.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean audit engagement
  through intake -> jurisdiction professional-standards assessment ->
  independence screening -> audit-opinion-issuance proposal (always
  escalates) -> human approval -> commit, then a clean tax-filing
  engagement through the same intake -> assess -> screen -> tax-filing-
  submission lifecycle, then shows six HARD holds (a jurisdiction with
  no spec-basis, an engagement with an undisclosed independence
  conflict, a trial balance that does not satisfy the fundamental
  accounting equation, an opinion issued against a tax-only engagement,
  a double-issuance of an already-issued opinion, and a double-filing
  of an already-submitted return) that never reach a human at all, and
  prints the audit ledger + the draft audit-opinion and tax-filing
  records."
  (:require [langgraph.graph :as g]
            [accounting.store :as store]
            [accounting.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :accountant :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== engagement/intake engagement-1 (JPN, audit, clean; trial balance balances) ==")
    (println (exec! actor "t1" {:op :engagement/intake :subject "engagement-1"
                                :patch {:id "engagement-1" :client "Sakura Manufacturing K.K."}} operator))

    (println "== jurisdiction/assess engagement-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "engagement-1"} operator))
    (println (approve! actor "t2"))

    (println "== independence/screen engagement-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :independence/screen :subject "engagement-1"} operator))
    (println (approve! actor "t3"))

    (println "== audit-opinion/issue engagement-1 (always escalates -- actuation/issue-opinion) ==")
    (let [r (exec! actor "t4" {:op :audit-opinion/issue :subject "engagement-1"} operator)]
      (println r)
      (println "-- human accountant approves --")
      (println (approve! actor "t4")))

    (println "== engagement/intake engagement-4 (JPN, tax-filing, clean) ==")
    (println (exec! actor "t5" {:op :engagement/intake :subject "engagement-4"
                                :patch {:id "engagement-4" :client "田中商事"}} operator))

    (println "== jurisdiction/assess engagement-4 (escalates -- human approves) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "engagement-4"} operator))
    (println (approve! actor "t6"))

    (println "== independence/screen engagement-4 (clean; escalates -- human approves) ==")
    (println (exec! actor "t7" {:op :independence/screen :subject "engagement-4"} operator))
    (println (approve! actor "t7"))

    (println "== tax-filing/submit engagement-4 (always escalates -- actuation/submit-filing) ==")
    (let [r (exec! actor "t8" {:op :tax-filing/submit :subject "engagement-4"} operator)]
      (println r)
      (println "-- human accountant approves --")
      (println (approve! actor "t8")))

    (println "== jurisdiction/assess engagement-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t9" {:op :jurisdiction/assess :subject "engagement-2"} operator))

    (println "== independence/screen engagement-5 (independence conflict -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t10" {:op :independence/screen :subject "engagement-5"} operator))

    (println "== jurisdiction/assess engagement-3 (escalates -- human approves; sets up the trial-balance test below) ==")
    (println (exec! actor "t11" {:op :jurisdiction/assess :subject "engagement-3"} operator))
    (println (approve! actor "t11"))

    (println "== audit-opinion/issue engagement-3 (trial balance does not satisfy assets = liabilities + equity -> HARD hold) ==")
    (println (exec! actor "t12" {:op :audit-opinion/issue :subject "engagement-3"} operator))

    (println "== audit-opinion/issue engagement-6 (a tax-filing-only engagement -> HARD hold, wrong engagement type) ==")
    (println (exec! actor "t13" {:op :audit-opinion/issue :subject "engagement-6"} operator))

    (println "== audit-opinion/issue engagement-1 AGAIN (double-issuance of an already-issued opinion -> HARD hold) ==")
    (println (exec! actor "t14" {:op :audit-opinion/issue :subject "engagement-1"} operator))

    (println "== tax-filing/submit engagement-4 AGAIN (double-filing of an already-submitted return -> HARD hold) ==")
    (println (exec! actor "t15" {:op :tax-filing/submit :subject "engagement-4"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft audit-opinion records ==")
    (doseq [r (store/opinion-history db)] (println r))

    (println "== draft tax-filing records ==")
    (doseq [r (store/filing-history db)] (println r))))
