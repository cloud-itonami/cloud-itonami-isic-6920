# Operator Quickstart — Accounting, bookkeeping and auditing activities

Shortest path from clone to a verified local dry-run for **ISIC 6920** (`cloud-itonami-isic-6920`).

## Who this is for

Licensed accounting or audit practices (CPAs, CAs, auditors) who want to:
- Fork this blueprint as the compliance scaffold for your practice
- Deploy it yourself under your own license and professional judgment
- Operate it on your own infrastructure (no SaaS)
- Extend it with your jurisdiction-specific professional standards and client integrations

## Prerequisites

- Clojure 1.12+ (`clojure --version`)
- Java 17+
- Git

No invented metrics; this is a governed OSS blueprint, not a hosted SaaS demo.

## 1. Clone

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-6920.git
cd cloud-itonami-isic-6920
```

## 2. Run tests

```bash
clojure -M:test
```

Expect green if maturity is `implemented`. Fix failures before operating.

## 3. Open the product face

```bash
open docs/index.html   # or: python3 -m http.server -d docs 8080
```

Publish: enable GitHub Pages on `main` `/docs`, or any static host.

## 4. Where the Governor sits

- Blueprint governor key: `audit-independence-governor`
- Likely source path: `accounting.governor.cljc`
- Pattern: advise → govern → phase-gate → commit | escalate | hold (itonami actor / ADR-2607011000)

## 5. Claim / go-live

- Free claim funnel: https://itonami.cloud/isco-1212/
- Paid path docs: https://itonami.cloud/docs/go-live.md
- Blueprint: `blueprint.edn`

## Constraints

- Do not invent users/revenue numbers for marketing
- No force-push; keep AGPL headers
- Secrets stay out of this repo
