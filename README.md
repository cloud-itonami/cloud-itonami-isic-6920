# cloud-itonami-isic-6920

Open Business Blueprint for **ISIC Rev.5 6920**: Accounting,
bookkeeping and auditing activities. This repository publishes an
accounting/auditing-practice actor -- client-engagement intake,
independence screening, audit-opinion issuance and tax-filing
submission -- as an OSS business that any qualified, licensed
accounting/audit practice can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492)) --
the first professional-services vertical (ISIC division 69) in this
fleet. Here it is **Ledger-LLM ⊣ Audit Independence Governor**.

> **Why an actor layer at all?** An LLM is great at drafting an
> engagement summary, normalizing intake, and checking whether a
> trial balance actually balances -- but it has **no notion of which
> jurisdiction's professional-standards/independence requirements are
> official, no license to issue a real audit opinion or submit a real
> tax filing, and no way to know on its own whether an engagement
> carries an undisclosed auditor-independence conflict**. Letting it
> issue an opinion or submit a filing directly invites fabricated
> jurisdiction citations, undisclosed independence conflicts, and
> silently-wrong bookkeeping a third party would actually rely on --
> and liability for whoever runs it. This project seals the Ledger-LLM
> into a single node and wraps it with an independent **Audit
> Independence Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers client-engagement intake through independence
screening, audit-opinion issuance and tax-filing submission. It does
**not**, by itself, hold a license to practice accounting/auditing in
any jurisdiction, and it does not claim to. It also does **not** model
a full materiality assessment -- no materiality threshold, no
immaterial-misstatement tolerance, no disclosure-adequacy review (see
`accounting.registry/trial-balance-difference`'s own docstring for the
honest simplification this makes: does the fundamental accounting
equation balance at all, not whether a small difference is
immaterial). Whoever deploys and operates a live instance (a licensed
accounting/audit practice) supplies the jurisdiction-specific license,
the real professional judgment and the real general-ledger/tax-filing
integrations, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so that
operator does not have to build the compliance layer from scratch for
every new market.

### Actuation

**Issuing a real audit opinion and submitting a real tax filing are
never autonomous, at any phase, by construction.** Two independent
layers enforce this (`accounting.governor`'s `:actuation/issue-
opinion`/`:actuation/submit-filing` high-stakes gate and `accounting.
phase`'s phase table, which never puts `:audit-opinion/issue`/`:tax-
filing/submit` in any phase's `:auto` set) -- see `accounting.phase`'s
docstring and `test/accounting/phase_test.clj`'s `audit-opinion-issue-
never-auto-at-any-phase`/`tax-filing-submit-never-auto-at-any-phase`.
The actor may draft, check and recommend; a human accountant/auditor is
always the one who actually issues an opinion or submits a filing.
Like `6512`/`6622`/`6520`/`6530`/`6820`, this actor has TWO actuation
events.

## The core contract

```
engagement intake + jurisdiction facts (accounting.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Ledger-LLM   │ ─────────────▶ │ Audit Independence        │  (independent system)
   │  (sealed)    │  + citations    │ Governor: spec-basis ·     │
   └──────────────┘                 │ evidence-incomplete ·      │
                             commit ◀────┼──────────▶ hold │ wrong-engagement-type ·
                                 │             │           │ independence-violation ·
                           record + ledger  escalate ─▶ human   trial-balance-out-of-
                                             (ALWAYS for         balance (pure ground-
                                              :audit-opinion/     truth recompute) ·
                                              issue /             already-issued ·
                                              :tax-filing/submit)  already-filed
```

**The Ledger-LLM never issues an audit opinion or submits a tax filing
the Audit Independence Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated jurisdiction
requirements; unsupported engagement evidence; a wrong-engagement-type
mismatch; an undisclosed independence conflict; a trial balance that
doesn't satisfy the fundamental accounting equation; a double issuance
or a double filing) force **hold** and *cannot* be approved past; a
clean opinion/filing proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk two clean lifecycles (audit-opinion issuance, tax-filing submission) + six HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a document-intake scanning
robot digitizes physical receipts/ledgers, under the actor, gated by
the independent **Audit Independence Governor**. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Audit Independence Governor, audit-opinion + tax-filing draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6920`). Unlike every prior actor in this fleet, this vertical's
engagement records are practice-specific rather than a shared cross-
operator data contract, so `accounting.*` runs on the generic identity/
forms/dmn/bpmn/audit-ledger stack only -- no bespoke domain capability
lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/accounting/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + audit-opinion/tax-filing history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded engagement, and double-issuance/double-filing guards check dedicated boolean facts rather than a `:status` value |
| `src/accounting/registry.cljc` | Audit-opinion + tax-filing draft records, plus `trial-balance-difference` (a real, foundational bookkeeping identity -- see docstring for what it does not model) |
| `src/accounting/facts.cljc` | Per-jurisdiction professional-standards/independence catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/accounting/ledgerllm.cljc` | **Ledger-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/independence-screening/opinion-issuance/filing-submission proposals |
| `src/accounting/governor.cljc` | **Audit Independence Governor** -- 5 HARD checks (spec-basis · evidence-incomplete · wrong-engagement-type · independence-violation, unconditional evaluation · trial-balance-out-of-balance, pure ground-truth recompute) + double-issuance/double-filing guards + 1 soft (confidence/actuation gate) |
| `src/accounting/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (opinion/filing always human; engagement intake is the ONLY auto-eligible op, no capital risk) |
| `src/accounting/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/accounting/sim.cljc` | demo driver |
| `test/accounting/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers client-engagement intake through independence
screening, audit-opinion issuance and tax-filing submission -- the
core governed lifecycle this blueprint's own `docs/business-model.md`
names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Engagement intake + per-jurisdiction professional-standards checklisting, HARD-gated on an official spec-basis citation (`:engagement/intake`/`:jurisdiction/assess`) | A full materiality assessment (materiality threshold, immaterial-misstatement tolerance, disclosure-adequacy review -- see `trial-balance-difference`'s docstring) |
| Independence screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:independence/screen`) | Real general-ledger/tax-filing-system integration, tax/regulatory reporting |
| Audit-opinion issuance, HARD-gated on the trial balance satisfying the fundamental accounting equation, wrong-engagement-type validity, and a double-issuance guard (`:audit-opinion/issue`) | Client-ledger reconciliation/bookkeeping workflows themselves |
| Tax-filing submission, HARD-gated on wrong-engagement-type validity and a double-filing guard (`:tax-filing/submit`) | |
| Immutable audit ledger for every intake/assessment/screening/opinion/filing decision | |

Extending coverage is additive: add the next gate (e.g. a materiality-
threshold check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`accounting.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `accounting.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `accounting.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `Ledger-LLM` + `Audit Independence Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the ten prior
actors' architecture. See `docs/adr/0001-architecture.md` for the
history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
