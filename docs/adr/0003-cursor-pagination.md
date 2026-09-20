# ADR-0003: Cursor (keyset) pagination for the transaction feed

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-09-19 |
| **Ticket** | FI-29 |
| **Deciders** | Oleksii Leheza |

## Context

The transaction feed (`GET /api/v1/transactions`) is the most-read endpoint in the product:
the home screen, the account screen and every filter or search render it, and the user
scrolls it on a phone. It reads from `transactions`, the one table that grows without bound,
partitioned by month on `booked_on` (ADR-0002). New entries arrive while the user is
scrolling — a bank-import can insert hundreds at once.

Two ways to page it:

- **Offset**: `?page=3&size=50` → `ORDER BY … LIMIT 50 OFFSET 150`.
- **Keyset (cursor)**: `?cursor=<opaque>&limit=50` → `WHERE (booked_on, id) < (:date, :id)
  ORDER BY booked_on DESC, id DESC LIMIT 50`.

## Decision

**Keyset pagination for the feed**, opaque cursor, newest first. Offset paging for the
admin-style lists where the collection is small and bounded (accounts, categories) —
CLAUDE.md 7 already splits the two.

Concretely:

- **Sort key is `(booked_on DESC, id DESC)`.** `booked_on` is the user's notion of order;
  `id` is a UUIDv7 (ADR-0001), so it is unique and itself time-ordered, which makes the pair
  a total order with no ties.
- **The cursor is the last row's `(booked_on, id)`**, encoded as URL-safe Base64 of
  `"YYYY-MM-DD|uuid"`, returned as `nextCursor` and absent on the last page. A cursor that
  does not decode is rejected with `422`, not silently treated as "start over".
- **The page query fetches `limit + 1`** rows to know whether a next page exists without a
  `COUNT(*)`.
- **Filters and search compose with the cursor**: account, category (whole or by split),
  type, date range and full-text `q` are all `WHERE` clauses added before the keyset
  predicate, so the same cursor contract holds for every view of the feed.
- **The index is the sort key**: `(owner_id, booked_on DESC, id DESC) WHERE deleted_at IS
  NULL`, created on the partitioned parent and inherited by every monthly partition, so a
  page is one index range scan over the partitions the date range touches.
- **No `total` is returned.** A total needs a full count per request and is meaningless in
  a stream that changes while you read it; the UI shows "load more", not "page 7 of 41".

## Consequences

- **+** **Constant cost per page.** `OFFSET n` reads and discards `n` rows before
  returning any; page 200 of a 10 000-row feed scans 9 950 rows to return 50. The keyset
  predicate seeks straight to the boundary, so page 200 costs the same as page 1. On a
  partitioned table this also prunes to the partitions after the cursor date.
- **+** **Stable under inserts.** With offsets, a row inserted at the top while scrolling
  shifts every later page by one, so the user sees one entry twice and misses another.
  A keyset page is defined by *where it starts*, so new rows above the cursor never
  duplicate or hide rows below it. `TransactionFeedTest` proves a full walk returns every
  row exactly once.
- **+** Matches the UI: infinite scroll on a phone, not numbered pages.
- **−** **No random access.** "Jump to page 12" is impossible; the date-range filter is
  the intended way to land somewhere. Accepted — nobody navigates a bank statement by page
  number.
- **−** **The sort order is fixed.** Sorting by amount or merchant would need a different
  keyset (`(amount, id)`) and its own index. If a sortable table view is ever wanted, it is
  a separate endpoint with an explicit key, not a `sort=` parameter on this one.
- **−** Clients must treat the cursor as opaque. The Base64 is a discouragement, not a
  secret; the server re-scopes every query by `owner_id`, so a forged cursor can only
  reposition within the caller's own feed.

## Alternatives considered

- **Offset pagination (`page`/`size`, Spring `Pageable`).** Simple and what Spring Data
  gives for free; used for the bounded admin lists. Rejected for the feed for the two reasons
  above: cost grows linearly with depth, and concurrent inserts make pages overlap or skip.
- **Timestamp-only cursor (`booked_on` alone).** Ambiguous: many entries share a day, so a
  page boundary inside a day either repeats or drops rows. The `id` tiebreaker is what makes
  the order total.
- **Cursor on `created_at` instead of `booked_on`.** Insertion order is stable but wrong for
  the user: an entry back-dated to last week must appear in last week, not at the top.
- **Offset with a snapshot token.** Freezes a result set server-side; heavy, stateful and
  still linear in depth. Rejected.
