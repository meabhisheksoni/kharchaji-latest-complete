# Add Expense Screen V2 — Design System Specification

> **PROJECT:** KharchaJi
> **Page Type:** Transaction Creation / Financial Form
> **Authority:** Aligned with `design-system/kharchaji/MASTER.md`

---

## 1. Visual Architecture & Layout Hierarchy

- **Screen Base:** `ModernColors.Eggshell` (`#F5F1E6` / Dark `#0F172A`)
- **TopAppBar:** Clean surface with back navigation button, "Add Expense" title in `SatoshiBold` / `Inter SemiBold`, and an active Target Date indicator pill (`#66AFF0` / `#FFFBF0`) displaying which date this transaction is being posted to.
- **Horizontal Guide Rails:** Strict `16.dp` padding across all form sections.

---

## 2. Component Design Specifications

### A. Hero Amount Input Card
- **Background:** `ModernColors.CardBg` (`#F9F5EA` / Dark `#1E293B`)
- **Border:** 1dp `ModernColors.CardBorder` (alpha 0.35f)
- **Corner Radius:** `20.dp`
- **Currency Symbol:** Large tactile `₹` prefix in `ModernColors.EggnogDark` (`#B89355`).
- **Amount Field:** `displayLarge` (36.sp, Bold), center-aligned or large left-aligned, auto-focused numeric keyboard with instant visual clarity.

### B. Transaction Details (Item Name & Notes)
- **Title Input:** `OutlinedTextField` with rounded `14.dp` corners, subtle border, placeholder "What did you spend on?".
- **Voice/Quick Suggestion Ready:** Clean leading shopping icon.

### C. Tactile Category Selector (Single-Tap Multi-Tier)
- **Layout:** High-density horizontal scrolling or 2-row flow of category chips with dynamic icons and category accent colors.
- **Selection State:** Active chips use full saturated badge with tinted container (`8%` soft tint) and `1.5dp` outline in category primary color.
- **Categories:** Dynamic stream from `TodoViewModel.primaryCategories` + `secondaryCategories` with quick "+ New" or popup button.

### D. Tactile Quantity Selector
- **Chips:** Quick predefined amounts (`250g`, `500g`, `1kg`, `1.5kg`, `2kg`) with active state tracking.
- **Custom Quantity & Units:** Compact inline row with custom digit input and units (`kg`, `g`, `items`, `L`).

### E. Attachment & Receipt Strip
- **Preview:** Horizontal lazy row with 72×72dp rounded thumbnails, delete badge, and tap-to-expand full viewer.
- **Actions:** Tactile "Camera" and "Gallery" pill buttons with Material 3 vector icons.

### F. Primary Action Bar (Fixed Bottom Rail)
- **Button:** Full-width tactile "Save Expense" button with `16.dp` corner radius, `EggnogDark` / emerald gradient or luxury slate finish, haptic feedback, and immediate responsive transition.
- **Elevation:** 2dp soft elevation with safe-drawing / navigation bar insets padding.
