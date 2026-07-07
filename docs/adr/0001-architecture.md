# ADR-0001: cloud-itonami-isic-6920 -- Ledger-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492` ADR-0001s (the pattern this ADR ports);
  ADR-2607071250/ADR-2607071320 (`6612`/`6492`, the first two verticals
  built outside ADR-2607032000's original insurance/real-estate batch
  -- this is the third)
- Context: Continuing the standing "pick a new ISIC blueprint vertical"
  direction past `6612`/`6492`, this ADR deepens `cloud-itonami-isic-
  6920` (accounting, bookkeeping and auditing activities) from
  `:blueprint` to `:implemented`, the eleventh actor in this fleet --
  the FIRST professional-services vertical (ISIC division 69), and the
  first actor whose blueprint names NO bespoke domain capability lib at
  all.

## Problem

Accounting/auditing practice bundles several distinct concerns under
one governed workflow:

1. **Jurisdiction professional-standards/independence disclosure
   correctness** -- is the required evidence for issuing an opinion or
   filing a return based on an official standard-setter, or invented?
2. **Auditor independence** -- does an engagement carry an undisclosed
   independence conflict? The accounting profession's own specific
   term (AICPA/PCAOB/IESBA/FRC/WPK "independence") for the exact same
   concept `casualty.governor`'s conflict-of-interest checks screen
   for -- reused a further time in this fleet.
3. **Engagement-type validity** -- does an audit-opinion-issuance
   attempt actually target an audit engagement, and a tax-filing
   attempt actually target a tax engagement? A genuinely NEW kind of
   check for this fleet: a type-tag validity check, neither arithmetic
   nor party-screening.
4. **Trial-balance correctness** -- does an engagement's own trial
   balance actually satisfy the fundamental accounting equation
   (assets = liabilities + equity)? A pure ground-truth recompute (the
   SAME shape `cloud-itonami-isic-6492`'s `affordability-exceeded-
   violations` establishes), but an EQUALITY-OF-SUMS check rather than
   a threshold check -- a sixth domain-specific formula, and a new
   arithmetic SHAPE within the "pure ground-truth recompute" family.
5. **Real actuation, twice** -- issuing a real audit opinion and
   submitting a real tax filing are both irreversible acts a third
   party will rely on.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run an accounting/audit practice with an
LLM" but "seal the LLM inside a trust boundary and layer evidence-
sufficiency, engagement-type validity, independence screening, trial-
balance correctness, audit and human-approval on top of it, while
structurally fixing both real actuation events as human-only."

## Decision

### 1. Ledger-LLM is sealed into the bottom node; it never issues or files directly

`accounting.ledgerllm` returns exactly five kinds of proposal: intake
normalization, jurisdiction professional-standards checklist,
independence screening, audit-opinion draft, and tax-filing draft. No
proposal writes the SSoT or commits a real audit opinion / tax filing
directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 accounting/auditing operation

`accounting.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. Independence screening reuses the unconditional-evaluation discipline for the accounting profession's own specific term

`independence-violation-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to a
specific op, so the screening op itself can HARD-hold on its own
finding) for a further application in this fleet -- citing the
accounting profession's OWN specific regulatory term ("auditor
independence," AICPA Rule 1.200 / PCAOB AS 1005 / FRC Ethical Standard
/ WPK's Unabhängigkeit) rather than dressing it up as a generic
"conflict of interest," an honest reflection of how this exact concept
is actually named and regulated in this profession.

### 4. `wrong-engagement-type-violations` is a genuinely NEW check kind: a type-tag validity check

Neither arithmetic (no recompute) nor party-screening (no identity/
relationship concern), this check simply verifies that an actuation op
targets an engagement of the matching `:engagement-type`. No sibling
actor in this fleet has needed this shape before, because no prior
actor bundled two structurally different actuation targets (audit vs.
tax) under ONE engagement entity with a type tag.

### 5. `trial-balance-out-of-balance-violations` reuses the pure-ground-truth-recompute shape on a SIXTH formula, with a new arithmetic flavor

`accounting.registry/trial-balance-difference`'s inputs (`:assets`/
`:liabilities`/`:equity`) are permanent facts already on the
engagement -- the SAME "no proposal inspection, no stored-verdict
lookup" shape `cloud-itonami-isic-6492`'s `affordability-exceeded-
violations` establishes. But where `6492`'s check is a THRESHOLD
comparison (a ratio against a ceiling), this check is an EQUALITY-OF-
SUMS comparison (one side of an equation against the other) -- a
different arithmetic flavor within the same architectural family.

### 6. A REAL bug WAS caught during test verification -- a defensive-guard gap in BOTH the advisor AND the governor

Both `propose-audit-opinion` (the advisor) and `trial-balance-out-of-
balance-violations` (the governor) initially called `trial-balance-
difference` UNCONDITIONALLY for any `:audit-opinion/issue` proposal/
check, without checking `:engagement-type` first. A `:tax-filing`
engagement has no `:assets`/`:liabilities`/`:equity` fields at all
(there is nothing to balance) -- calling the recompute against `nil`
values threw a `NullPointerException`, caught by the test suite
crashing outright (not a silent pass, not a misleading ledger entry --
an uncaught exception) on `wrong-engagement-type-for-opinion-is-held`.
Fixed in BOTH places by guarding the recompute on `(= :audit
(:engagement-type e))` first, deferring to `wrong-engagement-type-
violations` (which already correctly HARD-holds that mismatch) for
non-audit engagements. **The lesson this ADR records**: when a pure
ground-truth recompute's inputs are ONLY meaningful for a SUBSET of an
entity's possible type tags (unlike `6492`'s application, which always
has income/debt/requested-amount regardless of any type distinction),
BOTH the advisor's draft AND the governor's independent recompute must
guard on the type tag before touching the type-specific fields -- a
crash is the FASTEST possible signal that this guard is missing (no
silent misbehavior possible), but it must still be caught by running
the test suite, not assumed away.

### 7. Dual actuation events

`accounting.governor`'s `high-stakes` set has two members (`:actuation/
issue-opinion` and `:actuation/submit-filing`), matching `6512`'s/
`6622`'s/`6520`'s/`6530`'s/`6820`'s dual-actuation shape -- this domain
genuinely has two distinct real-world professional acts.

### 8. Double-issuance/double-filing guards check dedicated boolean facts, not `:status` -- deliberately sidestepping `6492`'s lifecycle trap

`already-issued-violations`/`already-filed-violations` check
`:opinion-issued?`/`:filing-submitted?`, dedicated booleans set once
and never cleared, rather than a `:status` value that could
legitimately advance past a checked state (the exact trap `cloud-
itonami-isic-6492`'s ADR-0001 documents in detail, rediscovered there
despite four prior safe reuses). This actor's `:status` never needs to
encode "has this actuation already happened" at all, so there is no
analogous status-lifecycle risk to fall into here -- a deliberate
architectural choice informed directly by the immediately-preceding
build's lesson.

### 9. No fabricated international opinion/filing-number standard

Same discipline as every sibling's registry: there is no single
international check-digit standard for an audit-opinion or tax-filing
reference number. `accounting.registry` therefore does not invent one;
it validates required fields and assigns a jurisdiction-scoped
sequence number only.

### 10. No bespoke capability lib

Unlike every prior actor in this fleet (each referencing its own
`kotoba-lang/*` capability lib), this vertical's engagement records are
practice-specific rather than a shared cross-operator data contract --
`accounting.*` runs on the generic identity/forms/dmn/bpmn/audit-ledger
stack only, per the blueprint's own explicit statement.

## Consequences

- (+) Accounting/auditing practice gets the same governed, auditable-
  actor treatment as the ten prior actors, extending the pattern to a
  genuinely different domain (professional services, ISIC division
  69) for the first time.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/accounting/phase_test.clj`'s `audit-
  opinion-issue-never-auto-at-any-phase`/`tax-filing-submit-never-
  auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/accounting/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- (+) `wrong-engagement-type-violations` is a genuine new check kind
  (type-tag validity) for this fleet, and `trial-balance-out-of-
  balance-violations` extends the pure-ground-truth-recompute family to
  an equality-of-sums arithmetic shape.
- (+) The NullPointerException bug (Decision 6) is now regression-
  tested by `test/accounting/governor_contract_test.clj`'s
  `wrong-engagement-type-for-opinion-is-held`/`wrong-engagement-type-
  for-filing-is-held`, and the lesson is generalized beyond this one
  build: guard type-specific-field recomputes on the type tag, in BOTH
  the advisor and the governor.
- (+) The double-issuance/double-filing guard design (Decision 8)
  demonstrates a lesson applied by DESIGN CHOICE informed by the
  immediately-preceding build's bug, rather than reused by direct
  analogy (which is exactly the failure mode that caused `6492`'s bug
  in the first place).
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `accounting.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) `trial-balance-difference` models only whether the fundamental
  accounting equation balances, not a full materiality assessment
  (materiality threshold, immaterial-misstatement tolerance,
  disclosure-adequacy review are out of scope -- see that fn's own
  docstring); real general-ledger/tax-filing-system integration and
  client-ledger reconciliation/bookkeeping workflows are all out of
  scope for this OSS actor -- each operator's responsibility (see
  README's coverage table).
- 38 tests / 172 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Keep `cloud-itonami-isic-6920` at `:blueprint` only | ❌ | The standing direction continues past `6612`/`6492`; professional services is a natural, well-precedented next domain |
| Dispatch both actuation events through ONE op by `:engagement-type` (mirroring `6629`'s single-dispatching-op design) | ❌ | This blueprint's Core Contract explicitly names TWO actuation events ("issuing an audit opinion or submitting a tax filing") as genuinely distinct real-world acts, matching the dual-actuation majority shape rather than `6629`'s bundled-activity shape |
| Model a full materiality assessment for conformance-test rigor | ❌ | Genuinely more complex real-world audit judgment that this R0 does not claim to model correctly -- honestly scoped to the fundamental accounting equation instead, same as every sibling's "starting catalog, not exhaustive" posture |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/accounting`) for consistency with every prior actor | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain |
