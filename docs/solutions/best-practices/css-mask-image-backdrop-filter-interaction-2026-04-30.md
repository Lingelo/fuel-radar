---
title: "CSS mask-image clips backdrop-filter -- use ::after pseudo-element instead"
date: 2026-04-30
category: best-practices
module: "Frontend - Styling"
problem_type: best_practice
component: frontend_stimulus
severity: medium
applies_when:
  - "Adding visual effects (fade, mask, gradient overlay) to containers with backdrop-filter"
  - "Implementing scroll-fade indicators on frosted-glass overlays"
  - "Working with CSS mask-image on blurred containers"
tags:
  - css-mask-image
  - backdrop-filter
  - css-interaction
  - frosted-glass
  - scroll-fade
  - pseudo-element
---

# CSS mask-image clips backdrop-filter -- use ::after pseudo-element instead

## Context

When building UI components with frosted-glass effects (using `backdrop-filter: blur()`), it's common to need visual fade indicators or scroll hints at container edges. The natural instinct is to apply a `mask-image` gradient to the same element that has `backdrop-filter`. This creates a compositing conflict where the mask clips the blurred backdrop, leaving unblurred content visible in the masked region.

This was discovered in the carburants-france codebase when adding a horizontal scroll-fade indicator to the mobile fuel filter container, which uses the `.glass` utility class (`backdrop-filter: blur(12px)`).

## Guidance

**Don't use `mask-image` on elements with `backdrop-filter`:**

```css
/* WRONG: mask-image clips the backdrop blur result */
.glass.scroll-fade {
  backdrop-filter: blur(12px);
  mask-image: linear-gradient(to right, black calc(100% - 2rem), transparent);
}
```

The transparent portion of the mask removes both the content AND the blur effect, exposing raw unblurred pixels beneath.

**Use a `::after` pseudo-element overlay instead:**

```css
/* CORRECT: pseudo-element sits above, doesn't affect compositing */
.scroll-fade-right {
  position: relative;
}

.scroll-fade-right::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 1.5rem;
  background: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.85));
  border-radius: 0 0.75rem 0.75rem 0;
  pointer-events: none;
  z-index: 1;
}
```

The pseudo-element is a separate paint layer that sits above the parent's content without touching the parent's compositing tree or `backdrop-filter` result.

## Why This Matters

Per the CSS Masking Module specification, `mask-image` is a compositing property that defines the alpha channel of an element's rendering. When `backdrop-filter` is applied to the same element:

1. `backdrop-filter: blur(...)` creates a blurred composite of content beneath the element
2. `mask-image` then applies its alpha mask to this entire composite
3. Transparent areas of the mask remove both the blurred content AND the blur effect itself

They are NOT independent CSS layers -- `mask-image` operates on the final rendered result, which includes `backdrop-filter`. A `::after` pseudo-element bypasses this because it's a child layer with its own stacking context.

## When to Apply

- A container uses `backdrop-filter` (blur, saturate, brightness, etc.)
- You need a visual fade, gradient overlay, or scroll indicator on that container
- The fade should respect the blurred backdrop without clipping it

In carburants-france, this applies to any `.glass` container (header, panels, bottom sheet, modals) that needs edge fades or scroll indicators.

## Examples

**Before (broken):**
```html
<div class="glass" style="mask-image: linear-gradient(to right, black 80%, transparent)">
  <div class="overflow-x-auto">...</div>
</div>
```
Result: Unblurred raw content visible in the fade zone on the right 20%.

**After (correct):**
```html
<div class="glass scroll-fade-right">
  <div class="overflow-x-auto">...</div>
</div>
```
Result: Blur effect preserved across the entire container; fade overlay sits cleanly above.

Key implementation details:
- Match the gradient end color to the `.glass` background (`rgba(255, 255, 255, 0.85)`)
- Use `pointer-events: none` so the overlay doesn't block clicks/taps
- Use `border-radius` on the pseudo-element to match the container's rounded corners

## Related

- Implementation: `src/index.css` (`.scroll-fade-right` class)
- Applied in: `src/App.tsx` (mobile fuel filter container)
- Plan: `docs/plans/2026-04-30-002-feat-pwa-icon-mobile-fuel-filter-plan.md`
