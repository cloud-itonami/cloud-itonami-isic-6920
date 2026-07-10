(ns accounting.store
  "SSoT for the accounting/auditing actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/accounting/store_contract_test.clj), which is the whole point:
  the actor, the Audit Independence Governor and the audit ledger never
  know which SSoT they run on.

  Like `casualty.store`'s/`reinsurance.store`'s/`credit.store`'s
  simpler entities, an ENGAGEMENT is acted on directly by both
  actuation ops -- no dynamically-filed sub-record. Double-issuance/
  double-filing guards check DEDICATED boolean facts
  (`:opinion-issued?`/`:filing-submitted?`), set once and never
  cleared, rather than a `:status` value -- deliberately sidestepping
  the status-lifecycle risk `cloud-itonami-isic-6492`'s ADR-0001
  documents in detail (this actor's `:status` never needs to encode
  'has this actuation already happened', so there is no analogous
  trap to fall into).

  The ledger stays append-only on every backend: 'which engagement was
  screened for independence, which audit opinion was issued, which tax
  filing was submitted, on what jurisdictional basis, approved by whom'
  is always a query over an immutable log -- the audit trail a client
  trusting a practice needs, and the evidence an operator needs if an
  opinion or a filing is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [accounting.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (engagement [s id])
  (all-engagements [s])
  (independence-of [s engagement-id] "committed independence screening verdict for an engagement, or nil")
  (assessment-of [s engagement-id] "committed jurisdiction professional-standards assessment, or nil")
  (ledger [s])
  (opinion-history [s] "the append-only audit-opinion history (accounting.registry drafts)")
  (filing-history [s] "the append-only tax-filing history (accounting.registry drafts)")
  (next-sequence [s jurisdiction] "next audit-opinion-number sequence for a jurisdiction")
  (filing-sequence [s jurisdiction] "next tax-filing-number sequence for a jurisdiction")
  (opinion-already-issued? [s engagement-id] "has an audit opinion already been issued for this engagement?")
  (filing-already-submitted? [s engagement-id] "has a tax filing already been submitted for this engagement?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-engagements [s engagements] "replace/seed the engagement directory (map id->engagement)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained engagement set (both engagement types) so
  the actor + tests run offline."
  []
  {:engagements
   {"engagement-1" {:id "engagement-1" :client "Sakura Manufacturing K.K." :engagement-type :audit
                     :independence-conflict? false :assets 10000000 :liabilities 6000000 :equity 4000000
                     :jurisdiction "JPN" :status :active}
    "engagement-2" {:id "engagement-2" :client "Atlantis Trading Co." :engagement-type :audit
                     :independence-conflict? false :assets 5000000 :liabilities 3000000 :equity 2000000
                     :jurisdiction "ATL" :status :active}
    "engagement-3" {:id "engagement-3" :client "鈴木工業" :engagement-type :audit
                     :independence-conflict? false :assets 10000000 :liabilities 6000000 :equity 3000000
                     :jurisdiction "JPN" :status :active}
    "engagement-4" {:id "engagement-4" :client "田中商事" :engagement-type :tax-filing
                     :independence-conflict? false
                     :jurisdiction "JPN" :status :active}
    "engagement-5" {:id "engagement-5" :client "佐藤ホールディングス" :engagement-type :audit
                     :independence-conflict? true :assets 5000000 :liabilities 2000000 :equity 3000000
                     :jurisdiction "JPN" :status :active}
    "engagement-6" {:id "engagement-6" :client "高橋物産" :engagement-type :tax-filing
                     :independence-conflict? false
                     :jurisdiction "JPN" :status :active}
    ;; engagement-7's client is deliberately "Northwind Capital Holdings
    ;; Ltd (demo)" -- byte-exact the SAME company as cloud-itonami-isic-
    ;; 8291's own sanctions-flagged demo company `co-200` (see 8291's
    ;; `dossier.store/demo-data`). LOCALLY this engagement looks entirely
    ;; clean (`:independence-conflict? false`, balanced trial balance) --
    ;; it exists purely to prove that `accounting.corporate-intel`'s
    ;; cross-reference into 8291 catches a flagged client that this
    ;; actor's own local-only independence check would silently miss.
    "engagement-7" {:id "engagement-7" :client "Northwind Capital Holdings Ltd (demo)"
                     :engagement-type :audit
                     :independence-conflict? false :assets 8000000 :liabilities 5000000 :equity 3000000
                     :jurisdiction "GBR" :status :active}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- issue-opinion!
  "Backend-agnostic `:engagement/mark-opinion-issued` -- looks up the
  engagement via the protocol and drafts the audit-opinion record, and
  returns {:result .. :engagement-patch ..} for the caller to
  persist."
  [s engagement-id]
  (let [e (engagement s engagement-id)
        seq-n (next-sequence s (:jurisdiction e))
        result (registry/register-audit-opinion engagement-id (:jurisdiction e) seq-n)]
    {:result result
     :engagement-patch {:opinion-issued? true
                        :opinion-number (get result "opinion_number")}}))

(defn- submit-filing!
  "Backend-agnostic `:engagement/mark-filing-submitted` -- looks up the
  engagement via the protocol and drafts the tax-filing record, and
  returns {:result .. :engagement-patch ..} for the caller to
  persist."
  [s engagement-id]
  (let [e (engagement s engagement-id)
        seq-n (filing-sequence s (:jurisdiction e))
        result (registry/register-tax-filing engagement-id (:jurisdiction e) seq-n)]
    {:result result
     :engagement-patch {:filing-submitted? true
                        :filing-number (get result "filing_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (engagement [_ id] (get-in @a [:engagements id]))
  (all-engagements [_] (sort-by :id (vals (:engagements @a))))
  (independence-of [_ id] (get-in @a [:independence id]))
  (assessment-of [_ engagement-id] (get-in @a [:assessments engagement-id]))
  (ledger [_] (:ledger @a))
  (opinion-history [_] (:opinions @a))
  (filing-history [_] (:filings @a))
  (next-sequence [_ jurisdiction] (get-in @a [:sequences jurisdiction] 0))
  (filing-sequence [_ jurisdiction] (get-in @a [:filing-sequences jurisdiction] 0))
  (opinion-already-issued? [_ engagement-id] (boolean (get-in @a [:engagements engagement-id :opinion-issued?])))
  (filing-already-submitted? [_ engagement-id] (boolean (get-in @a [:engagements engagement-id :filing-submitted?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (swap! a update-in [:engagements (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :independence/set
      (swap! a assoc-in [:independence (first path)] payload)

      :engagement/mark-opinion-issued
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (issue-opinion! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences jurisdiction] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :opinions registry/append result))))
        result)

      :engagement/mark-filing-submitted
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (submit-filing! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:filing-sequences jurisdiction] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :filings registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-engagements [s engagements] (when (seq engagements) (swap! a assoc :engagements engagements)) s))

(defn seed-db
  "A MemStore seeded with the demo engagement set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :independence {} :ledger [] :sequences {}
                           :opinions [] :filing-sequences {} :filings []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/independence payloads, ledger facts,
  opinion/filing records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:engagement/id               {:db/unique :db.unique/identity}
   :assessment/engagement-id    {:db/unique :db.unique/identity}
   :independence/engagement-id  {:db/unique :db.unique/identity}
   :ledger/seq                  {:db/unique :db.unique/identity}
   :opinion/seq                 {:db/unique :db.unique/identity}
   :filing/seq                  {:db/unique :db.unique/identity}
   :sequence/jurisdiction       {:db/unique :db.unique/identity}
   :filing-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- engagement->tx [{:keys [id client engagement-type independence-conflict? assets liabilities equity
                              jurisdiction status opinion-issued? opinion-number filing-submitted? filing-number]}]
  (cond-> {:engagement/id id}
    client                       (assoc :engagement/client client)
    engagement-type              (assoc :engagement/engagement-type engagement-type)
    (some? independence-conflict?) (assoc :engagement/independence-conflict? independence-conflict?)
    assets                       (assoc :engagement/assets assets)
    liabilities                  (assoc :engagement/liabilities liabilities)
    equity                       (assoc :engagement/equity equity)
    jurisdiction                 (assoc :engagement/jurisdiction jurisdiction)
    status                       (assoc :engagement/status status)
    (some? opinion-issued?)      (assoc :engagement/opinion-issued? opinion-issued?)
    opinion-number               (assoc :engagement/opinion-number opinion-number)
    (some? filing-submitted?)    (assoc :engagement/filing-submitted? filing-submitted?)
    filing-number                (assoc :engagement/filing-number filing-number)))

(def ^:private engagement-pull
  [:engagement/id :engagement/client :engagement/engagement-type :engagement/independence-conflict?
   :engagement/assets :engagement/liabilities :engagement/equity :engagement/jurisdiction
   :engagement/status :engagement/opinion-issued? :engagement/opinion-number
   :engagement/filing-submitted? :engagement/filing-number])

(defn- pull->engagement [m]
  (when (:engagement/id m)
    {:id (:engagement/id m) :client (:engagement/client m) :engagement-type (:engagement/engagement-type m)
     :independence-conflict? (boolean (:engagement/independence-conflict? m))
     :assets (:engagement/assets m) :liabilities (:engagement/liabilities m) :equity (:engagement/equity m)
     :jurisdiction (:engagement/jurisdiction m) :status (:engagement/status m)
     :opinion-issued? (boolean (:engagement/opinion-issued? m)) :opinion-number (:engagement/opinion-number m)
     :filing-submitted? (boolean (:engagement/filing-submitted? m)) :filing-number (:engagement/filing-number m)}))

(defrecord DatomicStore [conn]
  Store
  (engagement [_ id]
    (pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id id])))
  (all-engagements [_]
    (->> (d/q '[:find [?id ...] :where [?e :engagement/id ?id]] (d/db conn))
         (map #(pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id %])))
         (sort-by :id)))
  (independence-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?eid
                :where [?k :independence/engagement-id ?eid] [?k :independence/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ engagement-id]
    (dec* (d/q '[:find ?p . :in $ ?eid
                :where [?a :assessment/engagement-id ?eid] [?a :assessment/payload ?p]]
              (d/db conn) engagement-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (opinion-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :opinion/seq ?s] [?e :opinion/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (filing-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :filing/seq ?s] [?e :filing/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sequence/jurisdiction ?j] [?e :sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (filing-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :filing-sequence/jurisdiction ?j] [?e :filing-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (opinion-already-issued? [s engagement-id]
    (boolean (:opinion-issued? (engagement s engagement-id))))
  (filing-already-submitted? [s engagement-id]
    (boolean (:filing-submitted? (engagement s engagement-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (d/transact! conn [(engagement->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/engagement-id (first path) :assessment/payload (enc payload)}])

      :independence/set
      (d/transact! conn [{:independence/engagement-id (first path) :independence/payload (enc payload)}])

      :engagement/mark-opinion-issued
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (issue-opinion! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))
            next-n (inc (next-sequence s jurisdiction))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:sequence/jurisdiction jurisdiction :sequence/next next-n}
                      {:opinion/seq (count (opinion-history s)) :opinion/record (enc (get result "record"))}])
        result)

      :engagement/mark-filing-submitted
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (submit-filing! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))
            next-n (inc (filing-sequence s jurisdiction))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:filing-sequence/jurisdiction jurisdiction :filing-sequence/next next-n}
                      {:filing/seq (count (filing-history s)) :filing/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-engagements [s engagements]
    (when (seq engagements) (d/transact! conn (mapv engagement->tx (vals engagements)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:engagements ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [engagements]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-engagements s engagements))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo engagement set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
