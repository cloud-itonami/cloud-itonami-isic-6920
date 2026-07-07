(ns accounting.ledgerllm
  "Ledger-LLM client -- the *contained intelligence node* for the
  accounting/auditing actor.

  It normalizes engagement intake, drafts a per-jurisdiction
  professional-standards/independence checklist, screens engagements
  for an independence-conflict signal, drafts the audit-opinion
  action, and drafts the tax-filing action. CRITICAL: it is a smart-
  but-untrusted advisor. It returns a *proposal* (with a rationale +
  the fields it cited), never a committed record or a real audit
  opinion/tax filing. Every output is censored downstream by
  `accounting.governor` before anything touches the SSoT, and
  `:audit-opinion/issue`/`:tax-filing/submit` proposals NEVER auto-
  commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/issue-opinion | :actuation/submit-filing | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [accounting.facts :as facts]
            [accounting.registry :as registry]
            [accounting.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the client, engagement type or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "契約記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :engagement/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction professional-standards/independence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `accounting.facts` -- the Audit Independence Governor must reject
  this (never invent a jurisdiction's law)."
  [db {:keys [subject no-spec?]}]
  (let [e (store/engagement db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction e))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "accounting.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-independence
  "Independence screening draft. `:independence-conflict?` on the
  engagement record injects the failure mode: the Audit Independence
  Governor must HOLD, un-overridably, on any independence-conflict
  hit."
  [db {:keys [subject]}]
  (let [e (store/engagement db subject)]
    (cond
      (nil? e)
      {:summary "対象engagementが見つかりません" :rationale "no engagement record"
       :cites [] :effect :independence/set :value {:engagement-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (:independence-conflict? e)
      {:summary    (str (:client e) ": 独立性阻害要因を検出")
       :rationale  "スクリーニングが未開示の独立性阻害要因を検出。人手確認とホールドが必須。"
       :cites      [:independence-check]
       :effect     :independence/set
       :value      {:engagement-id subject :verdict :conflict}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:client e) ": 独立性阻害要因なし")
       :rationale  "独立性スクリーニング非該当。"
       :cites      [:independence-check]
       :effect     :independence/set
       :value      {:engagement-id subject :verdict :clear}
       :stake      nil
       :confidence 0.9})))

(defn- propose-audit-opinion
  "Draft the actual audit-OPINION-issuance action -- issuing a real
  audit opinion on a client's financial statements. ALWAYS `:stake
  :actuation/issue-opinion` -- this is a REAL-WORLD act (third parties
  will rely on the opinion), never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`accounting.phase`); the governor also always escalates on
  `:actuation/issue-opinion`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [e (store/engagement db subject)
        ;; A tax-filing-only engagement has no :assets/:liabilities/:equity at
        ;; all (there is nothing to balance) -- guard the recompute so a
        ;; wrong-engagement-type proposal (which the governor will HARD-hold
        ;; regardless) doesn't crash the advisor with a NullPointerException.
        diff (when (and e (= :audit (:engagement-type e))) (registry/trial-balance-difference e))
        balanced? (and diff (< (Math/abs diff) 0.01))]
    {:summary    (str subject " 向け監査意見発行提案"
                      (when e (str " (client=" (:client e) ")")))
     :rationale  (if e
                   (str "engagement-type=" (:engagement-type e) " trial-balance-difference=" diff)
                   "engagementが見つかりません")
     :cites      (if e [subject] [])
     :effect     :engagement/mark-opinion-issued
     :value      {:engagement-id subject}
     :stake      :actuation/issue-opinion
     :confidence (if balanced? 0.9 0.3)}))

(defn- propose-tax-filing
  "Draft the actual tax-FILING-submission action -- submitting a real
  tax filing on a client's behalf. ALWAYS `:stake :actuation/submit-
  filing` -- this is a REAL-WORLD act (the client becomes bound to the
  filing), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`accounting.phase`); the governor also always escalates on
  `:actuation/submit-filing`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [e (store/engagement db subject)]
    {:summary    (str subject " 向け申告書提出提案"
                      (when e (str " (client=" (:client e) ")")))
     :rationale  (if e
                   (str "engagement-type=" (:engagement-type e))
                   "engagementが見つかりません")
     :cites      (if e [subject] [])
     :effect     :engagement/mark-filing-submitted
     :value      {:engagement-id subject}
     :stake      :actuation/submit-filing
     :confidence (if e 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :engagement/intake       (normalize-intake db request)
    :jurisdiction/assess       (assess-jurisdiction db request)
    :independence/screen        (screen-independence db request)
    :audit-opinion/issue          (propose-audit-opinion db request)
    :tax-filing/submit              (propose-tax-filing db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは会計・監査事務所の意見発行・申告書提出エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:engagement/upsert|:assessment/set|:independence/set|"
       ":engagement/mark-opinion-issued|:engagement/mark-filing-submitted) "
       ":stake(:actuation/issue-opinion か :actuation/submit-filing か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess  {:engagement (store/engagement st subject)}
    :independence/screen  {:engagement (store/engagement st subject)}
    :audit-opinion/issue  {:engagement (store/engagement st subject)}
    :tax-filing/submit    {:engagement (store/engagement st subject)}
    {:engagement (store/engagement st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Audit Independence Governor
  escalates/holds -- an LLM hiccup can never auto-issue an opinion or
  auto-submit a filing."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :ledgerllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
