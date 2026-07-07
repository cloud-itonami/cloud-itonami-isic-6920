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
