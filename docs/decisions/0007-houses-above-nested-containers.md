# 0007. Houses above nested containers

- **Status:** Accepted
- **Date:** 2026-09-27
- **Change:** `trecos-v1` (design D4; spec places, organize)

## Context

The user needs several places at different addresses (their apartment, their parents' house), and sharing a house with others is a future goal.

## Decision

A **House** is a special top-level entity, separate from containers; below it, containers nest to any depth and items sit in any container. Items never hold items. Every row carries `houseId`. Built-in categories are global; custom categories, tags and custom fields belong to a house. QR codes are unique per house; scanning searches all houses. Moving and copying between houses are allowed, auto-creating missing custom categories and tags and asking on a QR clash. House fields: name (required), address, description, photos, place icon, colour; its value is automatic only. With one house the Home tab opens inside it; with two or more it lists them. A first run asks for the first house's name ("My home" pre-filled); the last house can't be deleted.

## Consequences

The data model supports sync and future sharing with no reshaping.
