# 0005. One quantity and one unit price

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D4; spec items)

## Context

The draft had both "quantity" / "number of items" and "estimated price" / "price per item".

## Decision

Merge them into `quantity` (a **whole number**, default 1) and `unitPrice` (optional, decimal, the estimated value of one unit). `totalValue = quantity × unitPrice` is calculated, never entered.

## Consequences

Money is stored as integer minor units. Quantity 0 to 999,999.
