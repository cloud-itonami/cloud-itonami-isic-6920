# Business Model: Accounting, bookkeeping and auditing activities

## Classification

- Repository: `cloud-itonami-isic-6920`
- ISIC Rev.5: `6920`
- Activity: accounting, bookkeeping and auditing services, including tax-return preparation, for client businesses and individuals
- Social impact: professional standards, data sovereignty, transparent audit

## Customer

- independent accounting/bookkeeping practices
- cooperative accounting-service pools
- community tax-preparation programs

## Offer

- client-ledger intake
- trial-balance/reconciliation proposal
- audit-opinion/tax-filing proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per client engagement
- support: monthly retainer with SLA
- migration: import from an incumbent bookkeeping system
- per-filing/audit-opinion fee

| Package | Customer | Price shape |
|---|---|---|
| Self-host starter | practice IT/ops lead | one-time implementation fee |
| Managed Starter | one independent accounting/audit practice, unlimited staff seats | ¥50,000/月 flat |
| Per-opinion / per-filing | practice billing its own client | fee per audit opinion or tax filing |
| Operator enablement | new licensed practice | training + certification |

**Market-anchored (2026-08-10)**: benchmarked against 7 real competitor
products, priced for an illustrative independent accounting/tax practice of
10 staff (10 seats) carrying 50–150 client engagements. **4 of the 7 publish
real per-user numbers on their own pricing pages** and those 4 are all US
practice-management platforms:

| Product | Discloses | Published price | Source |
|---|---|---|---|
| Karbon | yes | Team **$59**/month per user paid annually ($79 monthly); Business **$89** ($99 monthly); Enterprise custom | <https://karbonhq.com/pricing/> |
| Canopy | yes | Standard **$74**, Plus **$109**, Premium **$149** — user/month, annual billing; Enterprise custom | <https://www.getcanopy.com/pricing> |
| Jetpack Workflow | yes | Starter **$40**, Premium **$50** — per user/mo, billed annually | <https://jetpackworkflow.com/pricing/> |
| Financial Cents | yes | Solo **$19**, Team **$49**, Scale **$69** — per user/month billed annually | <https://financial-cents.com/pricing/> |
| TaxDome | has a pricing page, but it returns HTTP 403 to automated fetch | third-party aggregators report ~$58/user/mo — **not adopted**, no primary source | <https://taxdome.com/pricing/> |
| MyKomon (名南経営, 会計事務所専用グループウェア, 3,000+ practices) | **no** | 非公開 — 「本使用時の会費に関しては要問い合わせ」; the price table page itself is unreachable (403) | <https://help.mykomon.com/tool/for-office/price.html> |
| ジョブカン会計（税理士・会計事務所向け） | **no** | 非公開 — no yen amount anywhere on the page, only 無料相談 / 資料請求 | <https://all.jobcan.ne.jp/adviser/accounting/> |

Converting at ~¥150/$ for 10 seats: Jetpack Workflow **¥60,000–75,000/月**,
Financial Cents **¥73,500–103,500/月**, Karbon **¥88,500–133,500/月**, Canopy
**¥111,000–223,500/月**. The measured band is therefore **¥60,000–223,500/月**,
with the median of the four confirmed vendors sitting around **¥88,500/月**.

A second observation is worth recording because it shapes what this tier can
be: **the Japanese side of this market publishes nothing.** Both JP products
checked route to a quote. US practice management is price-transparent; the
incumbent tooling a Japanese 税理士事務所 actually buys is not. That means this
tier is, as far as could be measured, one of the first publicly priced numbers
for governed accounting-practice software in Japan — so it is set
conservatively against the US band rather than extrapolated upward from an
unobservable JP band.

**¥50,000/月 is set at roughly 55–60% of the confirmed median, and just below
the cheapest full suite.** Every product in the band above is a *practice
operating system* — task and workflow management for the whole firm, client
portal, document management, e-signature, time tracking and billing. This
actor replaces none of that. It is one module: engagement intake →
independence (conflict-of-interest) screening → an independent recompute of
whether the trial balance satisfies the fundamental accounting equation →
governed sign-off on the two actuation events (issuing an audit opinion,
submitting a tax filing) → an immutable ledger. Being narrower than Jetpack
Workflow, it is priced under Jetpack's 10-seat figure. It is nonetheless
priced flat rather than per-seat, and above a token amount, because what it
carries is the thing none of the four comparators enforce: an **undisclosed
independence conflict, a repeat opinion issuance, a repeat filing submission,
a fabricated professional-standards citation, or a trial balance that does not
balance each force a hold that cannot be approved past** — the control a
professional-liability review actually asks about. Those platforms track
deadlines; they do not refuse.

The honest limits of this number: TaxDome's figure was excluded because it
could not be confirmed from the vendor, and the whole JP half of the
comparison set is unpriced, so the band is anchored entirely on US per-seat
practice management converted at a fixed ¥150/$.

**Subscribe (2026-08-10)**: a live Stripe Payment Link for the Managed
Starter tier (¥50,000/月 flat) is available now —
[**subscribe to Managed Starter**](https://buy.stripe.com/6oUaEY2Mf4daaXC1zAeEo0k).
This is a no-code Stripe-hosted checkout; nothing in this repo's actor code
changed. After subscribing, open an
[issue](https://github.com/cloud-itonami/cloud-itonami-isic-6920/issues/new)
to arrange managed-tenant setup (manual fulfilment today, no automated
onboarding yet). **No accounting practice, audit firm or tax preparer has
claimed or subscribed to this tier yet — this is a live, working checkout with
zero paid tenants, not a claim of existing revenue.** Subscribing grants no
licence to practise: the subscriber remains the licensed accountant/auditor
who signs every opinion and every filing.

## Trust Controls

- no audit opinion is issued and no tax filing is submitted without
  human sign-off (a licensed accountant)
- a fabricated jurisdiction professional-standards citation,
  unsupported engagement evidence, a wrong-engagement-type mismatch,
  an undisclosed independence conflict, or a trial balance that does
  not satisfy the fundamental accounting equation (assets = liabilities
  + equity) -- each forces a hold, not an override
- an opinion cannot be issued twice and a filing cannot be submitted
  twice for the same engagement: a repeat attempt is held off this
  actor's own engagement facts alone, with no upstream comparison
  needed
- every intake, assessment, screening, opinion-issuance and filing-
  submission path is auditable
- emergency manual override paths remain outside LLM control
