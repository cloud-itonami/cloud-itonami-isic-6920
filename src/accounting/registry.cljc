(ns accounting.registry
  "Pure-function audit-opinion and tax-filing record construction -- an
  append-only accounting-practice book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for an audit-opinion or tax-filing reference
  number -- every practice/jurisdiction assigns its own reference
  format. This namespace does NOT invent one; it builds a jurisdiction-
  scoped sequence number and validates the record's required fields,
  the same honest, non-fabricating discipline `accounting.facts` uses.

  `trial-balance-difference` is a REAL, foundational bookkeeping
  identity (assets = liabilities + equity, the fundamental accounting
  equation), not an invented placeholder -- see its own docstring for
  the honest simplification it makes vs. a real materiality assessment
  (no materiality threshold, no immaterial-misstatement tolerance, no
  disclosure-adequacy review). Unlike every prior exact-match check in
  this fleet (`cloud-itonami-isic-6629`'s/`6520`'s/`6820`'s/`6612`'s,
  which all compare a TWO-INPUT computed value against a claimed
  figure), this is an EQUALITY-OF-SUMS check: does one side of the
  fundamental accounting equation actually equal the other, computed
  straight from the engagement's own permanent `:assets`/`:liabilities`/
  `:equity` fields, the SAME 'pure ground-truth recompute, no claimed
  figure to compare against' shape `cloud-itonami-isic-6492`'s
  `compute-debt-to-income-ratio` establishes -- applied here to a
  SIXTH domain-specific formula.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any general-ledger/tax-filing system. It builds the RECORD a
  practice would keep, not the act of issuing the opinion or
  submitting the filing itself (those are `accounting.operation`'s
  `:audit-opinion/issue` and `:tax-filing/submit`, always human-gated
  -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  licensed practitioner's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn trial-balance-difference
  "Pure computation of the fundamental accounting equation's slack:
  assets - (liabilities + equity). Zero means the trial balance
  actually balances; nonzero means it does not. A REAL, foundational
  bookkeeping identity (see ns docstring for what a full materiality
  assessment additionally considers: a materiality threshold, an
  immaterial-misstatement tolerance, disclosure-adequacy review --
  this R0 checks only whether the equation balances at all, not
  whether a small difference is immaterial)."
  [{:keys [assets liabilities equity]}]
  (- (double assets) (+ (double liabilities) (double equity))))

(defn register-audit-opinion
  "Validate + construct the AUDIT-OPINION registration DRAFT -- the
  practice's own legal act of issuing a real audit opinion on a
  client's financial statements. Pure function -- does not touch any
  real filing/publication system; it builds the RECORD a practice
  would keep. `accounting.governor` independently re-verifies the
  engagement's own trial balance against `trial-balance-difference`,
  and blocks a double-issuance of the same engagement, before this is
  ever allowed to commit."
  [engagement-id jurisdiction sequence]
  (when-not (and engagement-id (not= engagement-id ""))
    (throw (ex-info "audit-opinion: engagement_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "audit-opinion: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "audit-opinion: sequence must be >= 0" {})))
  (let [opinion-number (str (str/upper-case jurisdiction) "-OPIN-" (zero-pad sequence 6))
        record {"record_id" opinion-number
                "kind" "audit-opinion-draft"
                "engagement_id" engagement-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "opinion_number" opinion-number
     "certificate" (unsigned-certificate "AuditOpinionCertificate" opinion-number opinion-number)}))

(defn register-tax-filing
  "Validate + construct the TAX-FILING registration DRAFT -- the
  practice's own legal act of submitting a real tax filing on a
  client's behalf. Pure function -- does not touch any real tax-
  authority filing system; it builds the RECORD a practice would
  keep."
  [engagement-id jurisdiction sequence]
  (when-not (and engagement-id (not= engagement-id ""))
    (throw (ex-info "tax-filing: engagement_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "tax-filing: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "tax-filing: sequence must be >= 0" {})))
  (let [filing-number (str (str/upper-case jurisdiction) "-FILE-" (zero-pad sequence 6))
        record {"record_id" filing-number
                "kind" "tax-filing-draft"
                "engagement_id" engagement-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "filing_number" filing-number
     "certificate" (unsigned-certificate "TaxFilingCertificate" filing-number filing-number)}))

(defn append
  "Append an audit-opinion/tax-filing record, returning a NEW list
  (never mutate history in place)."
  [history result]
  (conj (vec history) (get result "record")))
