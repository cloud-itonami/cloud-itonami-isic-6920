(ns accounting.governor
  "Audit Independence Governor -- the independent compliance layer that
  earns the Ledger-LLM the right to commit. The LLM has no notion of
  jurisdictional professional-standards/independence law, whether an
  engagement's own trial balance actually balances, whether an
  engagement carries an undisclosed independence conflict, whether an
  op targets the right engagement type, or when an act stops being a
  draft and becomes a real-world audit opinion or tax filing, so this
  MUST be a separate system able to *reject* a proposal and fall back
  to HOLD -- the accounting/auditing analog of `cloud-itonami-isic-
  6512`'s CasualtyGovernor.

  Five checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete engagement evidence, a
  wrong-engagement-type mismatch, an undisclosed independence conflict,
  or a trial balance that doesn't actually balance). The confidence/
  actuation gate is SOFT: it asks a human to look (low confidence /
  actuation), and the human may approve -- but see `accounting.phase`:
  for `:stake :actuation/issue-opinion`/`:actuation/submit-filing` (a
  real audit opinion or a real tax filing) NO phase ever allows auto-
  commit either. Two independent layers agree that actuation is always
  a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`accounting.
                                       facts`), or invent one? Like
                                       `credit.governor`'s `:loan/
                                       disburse`, both actuation ops act
                                       directly on a pre-seeded
                                       engagement (see `accounting.
                                       store`'s own docstring) -- there
                                       is no 'engagement is missing'
                                       failure mode to guard against
                                       here.
    2. Evidence incomplete         -- for `:audit-opinion/issue`/`:tax-
                                       filing/submit`, are the
                                       jurisdiction's required
                                       engagement-letter/independence-
                                       declaration/working-papers docs
                                       actually satisfied?
    3. Wrong engagement type       -- does `:audit-opinion/issue`
                                       target an `:engagement-type
                                       :audit` engagement, and `:tax-
                                       filing/submit` target an
                                       `:engagement-type :tax-filing`
                                       one? A genuinely NEW kind of
                                       check for this fleet -- a type-
                                       tag validity check, not an
                                       arithmetic or party-screening
                                       one.
    4. Independence violation      -- does THIS proposal itself report
                                       an independence conflict (an
                                       `:independence/screen` that just
                                       found one), or does the
                                       engagement already carry one on
                                       file? Evaluated UNCONDITIONALLY
                                       (not scoped to a specific op),
                                       the SAME discipline `casualty.
                                       governor/sanctions-violations`
                                       established and `adjustment.
                                       governor`/`intermediation.
                                       governor`/`brokerage.governor`'s
                                       screening checks reused --
                                       applied here to the accounting
                                       profession's own specific term
                                       for this exact concept
                                       (auditor independence).
    5. Trial balance out of
       balance                       -- for `:audit-opinion/issue`
                                       only, does the engagement's own
                                       trial balance actually satisfy
                                       the fundamental accounting
                                       equation (assets = liabilities +
                                       equity, `accounting.registry/
                                       trial-balance-difference`)? A
                                       pure ground-truth recompute
                                       needing no proposal inspection or
                                       stored-verdict lookup, the SAME
                                       shape `cloud-itonami-isic-6492`'s
                                       `affordability-exceeded-
                                       violations` establishes, applied
                                       here to an EQUALITY-OF-SUMS
                                       check rather than a threshold
                                       check.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:audit-opinion/
                                       issue`/`:tax-filing/submit` (REAL
                                       professional acts) -> escalate.

  Two more guards, double-issuance and double-filing prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- each refuses to repeat the SAME
  actuation for the SAME engagement, off a dedicated boolean fact
  (`:opinion-issued?`/`:filing-submitted?`) rather than a `:status`
  value -- see `accounting.store`'s own docstring for why this
  sidesteps the status-lifecycle risk `cloud-itonami-isic-6492`'s
  ADR-0001 documents."
  (:require [accounting.facts :as facts]
            [accounting.registry :as registry]
            [accounting.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Issuing a real audit opinion and submitting a real tax filing are the
  two real-world actuation events this actor performs."
  #{:actuation/issue-opinion :actuation/submit-filing})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:audit-opinion/issue`/`:tax-filing/
  submit`) proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's professional-standards/independence
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :audit-opinion/issue :tax-filing/submit} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:audit-opinion/issue`/`:tax-filing/submit`, the jurisdiction's
  required engagement-letter/independence-declaration/working-papers
  evidence must actually be satisfied -- do not trust the advisor's
  self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:audit-opinion/issue :tax-filing/submit} op)
    (let [e (store/engagement st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction e) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(監査契約書/独立性確認書等)が充足していない状態での提案"}]))))

(defn- wrong-engagement-type-violations
  "`:audit-opinion/issue` must target an `:engagement-type :audit`
  engagement, and `:tax-filing/submit` must target an `:engagement-
  type :tax-filing` one -- a type-tag validity check, refuses to issue
  an opinion against a tax-only engagement or file a return against an
  audit-only one."
  [{:keys [op subject]} st]
  (let [e (store/engagement st subject)]
    (cond
      (and (= op :audit-opinion/issue) (not= :audit (:engagement-type e)))
      [{:rule :wrong-engagement-type
        :detail (str subject " は監査(audit)契約ではないため、監査意見は発行できない")}]

      (and (= op :tax-filing/submit) (not= :tax-filing (:engagement-type e)))
      [{:rule :wrong-engagement-type
        :detail (str subject " は税務(tax-filing)契約ではないため、申告書は提出できない")}])))

(defn- independence-violation-violations
  "An independence conflict -- reported by THIS proposal (e.g. an
  `:independence/screen` that itself just found one), or already on
  file in the store for the engagement (`:audit-opinion/issue`/`:tax-
  filing/submit`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :conflict (get-in proposal [:value :verdict]))
        engagement-id (when (contains? #{:independence/screen :audit-opinion/issue :tax-filing/submit} op) subject)
        hit-on-file? (and engagement-id (= :conflict (:verdict (store/independence-of st engagement-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :independence-violation
        :detail "未開示の独立性阻害要因のある契約を含む提案は進められない"}])))

(defn- close? [a b]
  (< (Math/abs (- (double a) (double b))) 0.01))

(defn- trial-balance-out-of-balance-violations
  "For `:audit-opinion/issue` only, INDEPENDENTLY recompute the
  engagement's trial-balance slack via `accounting.registry/trial-
  balance-difference` and refuse if it is nonzero -- needs no proposal
  inspection or stored-verdict lookup at all, since its inputs are
  permanent ground-truth fields already on the engagement (the SAME
  shape `credit.governor/affordability-exceeded-violations`
  establishes). Only applies when the engagement is actually an
  `:audit` engagement -- a `:tax-filing` engagement has no
  `:assets`/`:liabilities`/`:equity` at all (there is nothing to
  balance), and `wrong-engagement-type-violations` already HARD-holds
  that mismatch on its own; this check must not also attempt the
  recompute against absent fields."
  [{:keys [op subject]} st]
  (when (= op :audit-opinion/issue)
    (let [e (store/engagement st subject)]
      (when (= :audit (:engagement-type e))
        (let [diff (registry/trial-balance-difference e)]
          (when-not (close? 0 diff)
            [{:rule :trial-balance-out-of-balance
              :detail (str subject " の試算表が均衡していない(差額=" diff ")")}]))))))

(defn- already-issued-violations
  "For `:audit-opinion/issue`, refuses to issue a SECOND opinion for
  the SAME engagement, off a dedicated `:opinion-issued?` fact (never
  a `:status` value) -- see `accounting.store`'s own docstring for why
  this sidesteps the status-lifecycle risk `cloud-itonami-isic-6492`'s
  ADR-0001 documents."
  [{:keys [op subject]} st]
  (when (= op :audit-opinion/issue)
    (when (store/opinion-already-issued? st subject)
      [{:rule :already-issued
        :detail (str subject " には既に監査意見が発行されている")}])))

(defn- already-filed-violations
  "For `:tax-filing/submit`, refuses to submit a SECOND filing for the
  SAME engagement, off a dedicated `:filing-submitted?` fact."
  [{:keys [op subject]} st]
  (when (= op :tax-filing/submit)
    (when (store/filing-already-submitted? st subject)
      [{:rule :already-filed
        :detail (str subject " には既に申告書が提出されている")}])))

(defn check
  "Censors a Ledger-LLM proposal against the governor rules. Returns
   {:ok? bool :violations [..] :confidence c :escalate? bool :high-stakes? bool
    :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (wrong-engagement-type-violations request st)
                           (independence-violation-violations request proposal st)
                           (trial-balance-out-of-balance-violations request st)
                           (already-issued-violations request st)
                           (already-filed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
