(ns accounting.facts
  "Per-jurisdiction accounting/auditing professional-standards and
  independence regulatory catalog -- the G2-style spec-basis table the
  Audit Independence Governor checks every jurisdiction/assess proposal
  against ('did the advisor cite an OFFICIAL public source for this
  jurisdiction's engagement-evidence/independence requirements, or did
  it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official accounting/
  auditing standard-setter (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a real
  source, done -- never invent a jurisdiction's requirements to make
  coverage look bigger.

  Like `pension.facts`'s/`brokerage.facts`'s/`credit.facts`'s `USA`
  (not `USA-NY`), the PROFESSIONAL-STANDARDS/independence framework
  this catalog cites (AICPA Code of Professional Conduct / PCAOB / SEC
  Regulation S-X) is set at the national level in the US, even though
  individual CPA licensure itself is per-state -- this catalog cites
  the national standard-setters, the same posture every prior
  federally-set-standards entry in this fleet takes.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  engagement-letter/independence-declaration/working-papers evidence
  set submitted in some form; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "公認会計士・監査審査会 (Certified Public Accountants and Auditing Oversight Board, CPAAOB)"
          :legal-basis "公認会計士法 (Certified Public Accountants Act) -- 独立性に関する規定"
          :national-spec "日本公認会計士協会 倫理規則(独立性)"
          :provenance "https://www.fsa.go.jp/cpaaob/"
          :required-evidence ["監査契約書 (engagement letter)"
                              "独立性確認書 (independence declaration)"
                              "試算表・監査調書 (trial balance / working papers)"
                              "公認会計士登録証明書 (professional license/registration certificate)"]}
   "USA" {:name "United States"
          :owner-authority "American Institute of CPAs (AICPA) / Public Company Accounting Oversight Board (PCAOB)"
          :legal-basis "AICPA Code of Professional Conduct (Independence Rule 1.200) + SEC Regulation S-X Rule 2-01"
          :national-spec "PCAOB Auditing Standards (AS 1005, Independence)"
          :provenance "https://www.aicpa-cima.com/ https://pcaobus.org/"
          :required-evidence ["Engagement letter"
                              "Independence declaration"
                              "Trial balance / working papers"
                              "CPA license / registration certificate"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Financial Reporting Council (FRC)"
          :legal-basis "FRC Ethical Standard (independence requirements for auditors)"
          :national-spec "FRC Revised Ethical Standard, Section 3 (Independence)"
          :provenance "https://www.frc.org.uk/"
          :required-evidence ["Engagement letter"
                              "Independence declaration"
                              "Trial balance / working papers"
                              "Statutory auditor registration certificate"]}
   "DEU" {:name "Germany"
          :owner-authority "Wirtschaftsprüferkammer (WPK)"
          :legal-basis "Wirtschaftsprüferordnung (WPO) §§ 43 ff. (Unabhängigkeit)"
          :national-spec "IDW-Grundsätze zur Unabhängigkeit von Abschlussprüfern"
          :provenance "https://www.wpk.de/"
          :required-evidence ["Prüfungsauftrag (engagement letter)"
                              "Unabhängigkeitserklärung (independence declaration)"
                              "Summen- und Saldenliste / Arbeitspapiere (trial balance / working papers)"
                              "Wirtschaftsprüfer-Bestellungsurkunde (professional license/registration certificate)"]}
   "NLD" {:name "Netherlands"
          :owner-authority "Autoriteit Financiële Markten (AFM) / Koninklijke Nederlandse Beroepsorganisatie van Accountants (NBA)"
          :legal-basis "Wet toezicht accountantsorganisaties (Wta) art. 5 lid 1 (vergunningplicht: \"Het is verboden een wettelijke controle te verrichten zonder daartoe van de Autoriteit Financiële Markten een vergunning te hebben verkregen.\") en art. 19 lid 1 (onafhankelijkheid: \"Een accountantsorganisatie is onafhankelijk van de controlecliënt waarbij zij een wettelijke controle verricht en niet betrokken bij de besluitvorming van de controlecliënt ...\")"
          :national-spec "Besluit toezicht accountantsorganisaties (Bta), Hoofdstuk 6 art. 27-31 (nadere regels onafhankelijkheid o.g.v. Wta art. 19 lid 4)"
          :provenance "https://wetten.overheid.nl/BWBR0019468/ https://wetten.overheid.nl/BWBR0020184/ https://www.nba.nl/over-nba/register/"
          :required-evidence ["Opdrachtbevestiging (engagement letter)"
                              "Onafhankelijkheidsverklaring (independence declaration)"
                              "Proef- en saldibalans / controledossier (trial balance / working papers)"
                              "AFM-vergunning accountantsorganisatie / NBA-registratie externe accountant (audit firm AFM license / individual auditor NBA register entry, professional license/registration certificate)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to issue an audit
  opinion or submit a tax filing on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6920 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `accounting.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
