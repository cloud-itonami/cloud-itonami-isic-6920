(ns accounting.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300, Wave5
  rollout ledger, iteration 14) for cloud-itonami-isic-6920 (accounting/
  bookkeeping/auditing services).

  Drives the REAL actor stack (accounting.operation -> accounting.governor ->
  accounting.store) through a scenario built from real seeded demo data
  (`accounting.store/demo-data`, mirrored from the already-verified
  `accounting.sim` demo driver -- every id/op below is checked against that
  namespace's own output before reuse, not invented here). No hand-typed
  numbers, no timestamps, no randomness -- byte-identical across reruns
  against the same seed.

  Structure mirrors every other `render-html` namespace already shipped in
  this fleet (e.g. filmprodops/isic-5911): a thin harness (`exec!`/`approve!`
  over `langgraph.graph/run*`), a scenario builder (`run-demo!`), and a pure
  HTML renderer (`render`) reading only real fields off the `accounting.store`
  after the scenario actually ran."
  (:require [clojure.string :as str]
            [accounting.store :as store]
            [accounting.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness -----------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :accountant :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

;; ----------------------------- scenario -----------------------------

(defn run-demo!
  "Drives one OperationActor over a MemStore seeded with
  `accounting.store/demo-data` through:
    - 2 clean phase-3 auto-commit ops (`:engagement/intake`, the ONLY member
      of phase 3's `:auto` set -- see `accounting.phase`), on engagement-1
      and engagement-4.
    - 2 always-escalate actuation ops (`:audit-opinion/issue` /
      `:tax-filing/submit`, governor `high-stakes`, never in ANY phase's
      `:auto` set) followed by a human `approve!`, on engagement-1 and
      engagement-4 respectively -- each preceded by the `:jurisdiction/
      assess` + `:independence/screen` writes the governor's `evidence-
      incomplete-violations`/`independence-violation-violations` checks
      require to be on file first.
    - 6 DISTINCT real HARD-hold reasons, none reaching a human at all:
        :no-spec-basis                (jurisdiction/assess, engagement-2,
                                        jurisdiction ATL has no entry in
                                        `accounting.facts/catalog`)
        :independence-violation       (independence/screen, engagement-5,
                                        `:independence-conflict? true` on file)
        :trial-balance-out-of-balance (audit-opinion/issue, engagement-3,
                                        assets 10,000,000 != liabilities
                                        6,000,000 + equity 3,000,000)
        :evidence-incomplete +
        :wrong-engagement-type        (audit-opinion/issue, engagement-6,
                                        a `:tax-filing`-only engagement)
        :already-issued                (audit-opinion/issue, engagement-1
                                        AGAIN -- double-issuance guard)
        :already-filed                 (tax-filing/submit, engagement-4
                                        AGAIN -- double-filing guard)
  Returns the seeded `db` (an `accounting.store/MemStore`) after every op
  above has actually run through the graph -- `render` reads its real
  post-run state, never a hand-built fixture."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; -- clean phase-3 auto-commit: engagement/intake (engagement-1) --
    (exec! actor "t1" {:op :engagement/intake :subject "engagement-1"
                        :patch {:id "engagement-1" :client "Sakura Manufacturing K.K."}})

    ;; -- engagement-1's audit-opinion/issue needs assess + screen on file --
    (exec! actor "t2" {:op :jurisdiction/assess :subject "engagement-1"})
    (approve! actor "t2")
    (exec! actor "t3" {:op :independence/screen :subject "engagement-1"})
    (approve! actor "t3")

    ;; -- always-escalate actuation: audit-opinion/issue (engagement-1) --
    (exec! actor "t4" {:op :audit-opinion/issue :subject "engagement-1"})
    (approve! actor "t4")

    ;; -- clean phase-3 auto-commit: engagement/intake (engagement-4) --
    (exec! actor "t5" {:op :engagement/intake :subject "engagement-4"
                        :patch {:id "engagement-4" :client "田中商事"}})

    ;; -- engagement-4's tax-filing/submit needs assess + screen on file --
    (exec! actor "t6" {:op :jurisdiction/assess :subject "engagement-4"})
    (approve! actor "t6")
    (exec! actor "t7" {:op :independence/screen :subject "engagement-4"})
    (approve! actor "t7")

    ;; -- always-escalate actuation: tax-filing/submit (engagement-4) --
    (exec! actor "t8" {:op :tax-filing/submit :subject "engagement-4"})
    (approve! actor "t8")

    ;; -- HARD hold #1: no-spec-basis (engagement-2, jurisdiction ATL) --
    (exec! actor "t9" {:op :jurisdiction/assess :subject "engagement-2"})

    ;; -- HARD hold #2: independence-violation (engagement-5, conflict on file) --
    (exec! actor "t10" {:op :independence/screen :subject "engagement-5"})

    ;; -- HARD hold #3: trial-balance-out-of-balance (engagement-3) --
    (exec! actor "t11" {:op :jurisdiction/assess :subject "engagement-3"})
    (approve! actor "t11")
    (exec! actor "t12" {:op :audit-opinion/issue :subject "engagement-3"})

    ;; -- HARD hold #4: evidence-incomplete + wrong-engagement-type (engagement-6) --
    (exec! actor "t13" {:op :audit-opinion/issue :subject "engagement-6"})

    ;; -- HARD hold #5: already-issued (engagement-1 double-issuance) --
    (exec! actor "t14" {:op :audit-opinion/issue :subject "engagement-1"})

    ;; -- HARD hold #6: already-filed (engagement-4 double-filing) --
    (exec! actor "t15" {:op :tax-filing/submit :subject "engagement-4"})

    db))

;; ----------------------------- render helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- yn [b] (if b "yes" "no"))

(defn- last-fact-for
  "The last ledger fact (commit or hold) whose `:subject` is `subject` --
  `accounting.operation`/`accounting.governor` both key every fact on
  `:subject` (confirmed by reading `commit-fact`/`hold-fact`, NOT assumed)."
  [ledger subject]
  (last (filter #(= subject (:subject %)) ledger)))

(defn- status-cell
  "Same status-rendering shape every other `render-html` in this fleet uses:
  cond on the fact's `:t`, rendering the HOLD `:rule`(s) from `:violations`
  when held. `:approval-granted`/`:approval-requested` are kept for
  structural parity with the rest of the fleet even though this repo's own
  `accounting.store` ledger only ever persists `:committed`/`:governor-hold`/
  `:approval-rejected` facts (verified by reading `accounting.operation`'s
  `:commit`/`:hold` nodes) -- they fall through harmlessly on this repo's
  real data."
  [fact]
  (cond
    (nil? fact)
    "<span class=\"muted\">in progress</span>"

    (= :committed (:t fact))
    "<span class=\"ok\">committed</span>"

    (= :approval-granted (:t fact))
    "<span class=\"ok\">approved</span>"

    (= :approval-requested (:t fact))
    "<span class=\"warn\">pending approval</span>"

    (= :approval-rejected (:t fact))
    (str "<span class=\"err\">rejected: "
         (esc (str/join ", " (map (comp name :rule) (:violations fact)))) "</span>")

    (= :governor-hold (:t fact))
    (str "<span class=\"critical\">HOLD: "
         (esc (str/join ", " (map (comp name :rule) (:violations fact)))) "</span>")

    :else
    "<span class=\"muted\">in progress</span>"))

;; ----------------------------- static action-gate contract -----------------------------
;; A static description of `accounting.governor`'s 7 HARD checks + confidence/
;; actuation gate, and `accounting.phase`'s phase-3 write/auto sets -- read
;; directly off `governor.cljc`/`phase.cljc` (see `action-gate-table` below),
;; not invented and not derived from `db`.

(def ^:private phase-3-write-ops
  "engagement/intake, jurisdiction/assess, independence/screen, audit-opinion/issue, tax-filing/submit")

(def ^:private phase-3-auto-ops
  "engagement/intake (only member of phase 3's :auto set -- audit-opinion/issue and tax-filing/submit are permanently absent from every phase's :auto set)")

;; ----------------------------- render -----------------------------

(def ^:private style-block
  "table { width: 100%; border-collapse: collapse; font-size: 14px; }
.ok { color: #137a3f; }
body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }
header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }
h2 { margin-top: 0; font-size: 15px; }
.warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }
main { max-width: 980px; margin: 24px auto; padding: 0 20px; }
header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }
.muted { color: #888; font-size: 13px; }
.critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }
th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }
code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }")

(defn- engagements-table [db]
  (let [engagements (store/all-engagements db)
        ledger (store/ledger db)]
    (str "<table><thead><tr>"
         "<th>ID</th><th>Client</th><th>Type</th><th>Jurisdiction</th>"
         "<th>Independence conflict?</th><th>Opinion issued?</th><th>Filing submitted?</th>"
         "<th>Last action</th>"
         "</tr></thead><tbody>"
         (str/join
          (map (fn [e]
                 (str "<tr>"
                      "<td><code>" (esc (:id e)) "</code></td>"
                      "<td>" (esc (:client e)) "</td>"
                      "<td>" (esc (name (:engagement-type e))) "</td>"
                      "<td>" (esc (:jurisdiction e)) "</td>"
                      "<td>" (esc (yn (:independence-conflict? e))) "</td>"
                      "<td>" (esc (yn (:opinion-issued? e))) "</td>"
                      "<td>" (esc (yn (:filing-submitted? e))) "</td>"
                      "<td>" (status-cell (last-fact-for ledger (:id e))) "</td>"
                      "</tr>"))
               engagements))
         "</tbody></table>")))

(defn- committed-records-table [db]
  (let [opinions (mapv #(assoc % "kind_label" "audit opinion") (store/opinion-history db))
        filings  (mapv #(assoc % "kind_label" "tax filing") (store/filing-history db))
        records  (concat opinions filings)]
    (str "<table><thead><tr>"
         "<th>Record ID</th><th>Kind</th><th>Engagement</th><th>Jurisdiction</th><th>Immutable?</th>"
         "</tr></thead><tbody>"
         (str/join
          (map (fn [r]
                 (str "<tr>"
                      "<td><code>" (esc (get r "record_id")) "</code></td>"
                      "<td>" (esc (get r "kind_label")) " (<code>" (esc (get r "kind")) "</code>)</td>"
                      "<td><code>" (esc (get r "engagement_id")) "</code></td>"
                      "<td>" (esc (get r "jurisdiction")) "</td>"
                      "<td>" (esc (yn (get r "immutable"))) "</td>"
                      "</tr>"))
               records))
         "</tbody></table>")))

(defn- action-gate-table []
  (str "<table><thead><tr><th>Rule</th><th>Applies to</th><th>Description</th></tr></thead><tbody>"
       "<tr><td><code>no-spec-basis</code></td><td>jurisdiction/assess, audit-opinion/issue, tax-filing/submit</td>"
       "<td>proposal cites no OFFICIAL spec-basis (<code>accounting.facts</code>) for the engagement's jurisdiction</td></tr>"
       "<tr><td><code>evidence-incomplete</code></td><td>audit-opinion/issue, tax-filing/submit</td>"
       "<td>jurisdiction's required evidence checklist (engagement letter / independence declaration / working papers / license) not satisfied on file</td></tr>"
       "<tr><td><code>wrong-engagement-type</code></td><td>audit-opinion/issue, tax-filing/submit</td>"
       "<td>audit-opinion/issue must target an :audit engagement; tax-filing/submit must target a :tax-filing engagement</td></tr>"
       "<tr><td><code>independence-violation</code></td><td>independence/screen, audit-opinion/issue, tax-filing/submit</td>"
       "<td>an independence conflict -- reported by this proposal OR already on file for the engagement -- evaluated unconditionally</td></tr>"
       "<tr><td><code>trial-balance-out-of-balance</code></td><td>audit-opinion/issue</td>"
       "<td>engagement's own assets != liabilities + equity, independently recomputed (<code>accounting.registry/trial-balance-difference</code>)</td></tr>"
       "<tr><td><code>already-issued</code></td><td>audit-opinion/issue</td>"
       "<td>a second audit opinion for the same engagement (double-issuance guard, <code>:opinion-issued?</code> fact)</td></tr>"
       "<tr><td><code>already-filed</code></td><td>tax-filing/submit</td>"
       "<td>a second tax filing for the same engagement (double-filing guard, <code>:filing-submitted?</code> fact)</td></tr>"
       "<tr><td><code>confidence-floor / high-stakes</code></td><td>any op</td>"
       "<td>confidence below 0.6, OR the op is audit-opinion/issue / tax-filing/submit (<code>:actuation/issue-opinion</code>, <code>:actuation/submit-filing</code>) -- escalate to a human, NEVER auto-commit at any phase</td></tr>"
       "</tbody></table>"
       "<p class=\"muted\">Phase 3 (<code>supervised-auto</code>) write-ops: <code>" (esc phase-3-write-ops) "</code>.<br>"
       "Phase 3 auto-commit-eligible ops: <code>" (esc phase-3-auto-ops) "</code>.</p>"))

(defn- audit-ledger-table [db]
  (let [ledger (store/ledger db)]
    (str "<table><thead><tr>"
         "<th>#</th><th>Op</th><th>Subject</th><th>Disposition</th><th>Rule(s) / basis</th><th>Summary</th>"
         "</tr></thead><tbody>"
         (str/join
          (map-indexed
           (fn [i f]
             (str "<tr>"
                  "<td>" (inc i) "</td>"
                  "<td><code>" (esc (subs (str (:op f)) 1)) "</code></td>"
                  "<td><code>" (esc (:subject f)) "</code></td>"
                  "<td>" (if (= :commit (:disposition f))
                           "<span class=\"ok\">commit</span>"
                           "<span class=\"critical\">hold</span>") "</td>"
                  "<td>" (if (seq (:violations f))
                           (esc (str/join ", " (map (comp name :rule) (:violations f))))
                           "<span class=\"muted\">-</span>") "</td>"
                  "<td>" (esc (or (:summary f)
                                  (some-> f :violations first :detail))) "</td>"
                  "</tr>"))
           ledger))
         "</tbody></table>")))

(defn render
  "Renders the full operator-console HTML page from `db`'s real post-run
  state (`store/all-engagements`, `store/opinion-history`, `store/filing-
  history`, `store/ledger`) -- every table cell traces to a field actually
  read off `db` after `run-demo!` executed the graph."
  [db]
  (str "<!doctype html>\n<html lang=\"en\">\n<head>\n"
       "<meta charset=\"utf-8\">\n"
       "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
       "<title>cloud-itonami-isic-6920 &middot; accounting.render-html</title>\n"
       "<style>\n" style-block "\n</style>\n"
       "</head>\n<body>\n"
       "<header class=\"bar\">"
       "<h1>cloud-itonami-isic-6920 &middot; Audit Independence Governor operator console</h1>"
       "<span class=\"badge\">accounting.render-html &middot; generated by actually running the actor (langgraph-clj StateGraph, no hand-typed values)</span>"
       "</header>\n"
       "<main>\n"
       "<div class=\"card\"><h2>Engagements</h2>" (engagements-table db) "</div>\n"
       "<div class=\"card\"><h2>Committed records (this run)</h2>" (committed-records-table db) "</div>\n"
       "<div class=\"card\"><h2>Action gate</h2>" (action-gate-table) "</div>\n"
       "<div class=\"card\"><h2>Audit ledger (this run)</h2>" (audit-ledger-table db) "</div>\n"
       "</main>\n"
       "</body>\n</html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out)))
