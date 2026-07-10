(ns accounting.corporate-intel
  "Optional integration with `cloud-itonami-isic-8291` (Dossier-LLM ⊣
  DisclosureGovernor corporate/compliance intelligence actor) -- cross-
  references an audit engagement's CLIENT COMPANY against 8291's sourced
  company-profile data via the SAME governed `:disclosure/query` op any
  other licensed consumer would use (extended in 8291 commit `b8adf8c`,
  ADR-2607110400, to resolve by `:company-name` and to carry a `:value
  {:company-id .. :flags {...}}`). There is no bypass of 8291's own
  DisclosureGovernor from this side either. In particular, when 8291
  escalates a real hit for its OWN human reviewer to confirm, this
  namespace does NOT peek at Dossier-LLM's un-vetted draft proposal to
  get an early answer -- it reports `:pending-human-review?` and lets
  `accounting.ledgerllm/screen-independence` treat that the same as any
  other inconclusive signal (escalate, never silently clear).

  Swappable like every other dependency in this fleet (Store/Advisor/
  Phase): `screen-company` defaults to a demo 8291 MemStore + fresh
  actor per call, but takes an already-built `:actor` for production
  (one built once, against a real Store, under a real contract this
  blueprint's operator negotiated with the 8291 operator)."
  (:require [langgraph.graph :as g]
            [dossier.store :as dstore]
            [dossier.operation :as dop]))

(def default-tenant
  "This blueprint's own tenant id under an 8291 contract. A real
  deployment registers this (or an operator-chosen tenant id) with
  whichever 8291 instance/operator it has a compliance-tier contract
  with."
  "cloud-itonami-isic-6920")

(defn demo-store
  "An 8291 MemStore seeded with 8291's own demo data, PLUS a contract for
  THIS blueprint's tenant at `:tier/compliance` (a company-profile query
  that wants `:flags` requires at least that tier -- see cloud-itonami-
  isic-8291's `dossier.policy/tier-columns`). Replaces 8291's own demo
  tenant-acme/tenant-basic/tenant-graph contracts entirely: this is
  6920's OWN isolated offline view, not a shared runtime instance with
  8291's demo fixtures."
  []
  (-> (dstore/seed-db)
      (dstore/with-contracts
       {default-tenant {:tenant default-tenant :tier :tier/compliance
                         :active? true :purpose :client-acceptance-screening}})))

(defn build
  "Compiles an 8291 OperationActor bound to `store` (default: `demo-store`)."
  ([] (build (demo-store)))
  ([store] (dop/build store)))

(defn screen-company
  "Runs a `:disclosure/query` op against 8291 for `client-name` (a client
  engagement's `:client` COMPANY name). Returns one of:
    {:company-id .. :flags {...}}   -- a governor-approved answer
      (disposition :commit): the client's own registry facts, including
      whatever sanctions/compliance flags 8291 has sourced for it.
    {:pending-human-review? true :reason kw}  -- 8291 itself escalated a
      real hit (e.g. a sanctions flag) to ITS OWN human reviewer; treat
      as inconclusive, not as a confirmed clean/flagged result.
    {:held? true :reason [kw ..]}   -- the query itself was rejected by
      8291's DisclosureGovernor (e.g. this tenant's contract is missing/
      inactive/wrong tier on the Store actually in use) -- a
      configuration problem on the calling side, not a finding about
      `client-name`. Never silently treated as clean.

  opts:
    :actor     -- a pre-built 8291 OperationActor (default: fresh `build`)
    :tenant    -- tenant id to query under (default: `default-tenant`)
    :thread-id -- langgraph-clj thread id (default: derived from `client-name`)"
  ([client-name] (screen-company client-name {}))
  ([client-name {:keys [actor tenant thread-id]
                 :or   {actor (build) tenant default-tenant}}]
   (let [thread-id (or thread-id (str "compcheck-" tenant "-" client-name))
         res (g/run* actor
                     {:request {:op :disclosure/query :subject tenant :company-name client-name}
                      :context {:actor-id default-tenant :actor-role :client :tenant tenant}}
                     {:thread-id thread-id})]
     (case (get-in res [:state :disposition])
       :commit    (get-in res [:state :record :value])
       :escalate  {:pending-human-review? true
                   :reason (-> res :state :audit last :reason)}
       :hold      {:held? true
                   :reason (-> res :state :audit last :basis)}
       {:held? true :reason [:corporate-intel-actor-error]}))))
