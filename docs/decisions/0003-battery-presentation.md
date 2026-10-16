# 0003: Battery presentation

Date: 2026-10-16 · Status: accepted

## Decision

Lead with the component's reported status (good / low / charge now). Show a percentage only where the
component reports one. Show pack estimates from voltage as "≈" and secondary. Keep voltage, read method and
age in the component detail. When there's no reported status, derive one with SRAM's thresholds.

## Why

The reported status is what the component itself says and matches SRAM's app. SRAM has no voltage curve
for AXS packs, so any estimate is ours and must look like one.
