# KharchaJi — Master Design System Specification (M3 & Jetpack Compose)

> **SOURCE OF TRUTH:** This document is the global visual and interaction authority for **KharchaJi**.
> When building or refactoring any screen, follow this specification. If a page-specific file exists in `design-system/kharchaji/pages/[page-name].md`, its rules override this master specification.

---

## 1. Project Overview & Design Philosophy

* **Application:** KharchaJi Personal Finance & Expense Tracker
* **Platform:** Android (Jetpack Compose, Material 3)
* **Design Archetype:** Modern FinTech / Financial Dashboard
* **Primary Visual Style:** Warm Luxury Linen / Soft Cream (Light Mode) × Deep OLED Charcoal (Dark Mode) + Tactile Micro-Cards
* **Design Dials:**
  * **Visual Density:** `8/10` (Dense / Dashboard — high information-to-screen ratio without clutter)
  * **Design Variance:** `6/10` (Balanced & Modern — structured grid with subtle asymmetry for key financial metrics)
  * **Motion Intensity:** `6/10` (Fluid, contextual micro-interactions; physics-based springs for rolling numbers and expand/collapse)

---

## 2. Color System & Semantic Tokens

KharchaJi uses a signature dual-palette system designed for high readability in daylight and battery-efficient contrast in dark mode.

### A. Surface & Background Tokens (Jetpack Compose)

| Token Name | Light (Linen / Soft Cream) | Dark (OLED / Night) | Usage |
| :--- | :--- | :--- | :--- |
| `Eggshell` | `#F5F1E6` | `#0F172A` | Screen base background |
| `SoftCream` | `#FFFBF0` | `#192134` | Secondary background / TopAppBar fill |
| `CardBg` | `#F9F5EA` | `#1E293B` | Standard card surface, transaction items |
| `CardBorder` | `#C2BEB7` (alpha 0.35f) | `rgba(255,255,255,0.10f)` | 1dp crisp card separation border |
| `HairlineDivider`| `rgba(0,0,0,0.06f)` | `rgba(255,255,255,0.08f)` | Subtle 0.5dp/1dp section dividers |

### B. Typography & Text Hierarchy Tokens

| Token Name | Hex Value | WCAG Contrast | Usage |
| :--- | :--- | :--- | :--- |
| `TextPrimary` | `#212121` (Light) / `#FFFFFF` (Dark) | `>12:1` (AAA) | Hero spend amounts, headlines, card titles |
| `EggnogDark` | `#B89355` | `>4.8:1` (AA) | Brand accent text, high-contrast labels, active headers |
| `DateText` | `#A18A58` | `>4.5:1` (AA) | Date navigator labels, secondary headers |
| `TextMuted` | `#757575` (Light) / `#94A3B8` (Dark) | `>4.5:1` (AA) | Transaction subtitles, timestamps, notes |

### C. Financial Status & Category Semantics

| Financial State | Brand Color | 8% Soft Tint Background | Meaning & Usage |
| :--- | :--- | :--- | :--- |
| **Groceries** | `#439D46` (Forest Green) | `#E8F5E9` | Daily staples, grocery stores |
| **Transport** | `#41C86A` (Emerald) | `#E0F7FA` | Fuel, transit, rideshare |
| **Utilities** | `#00BCD4` (Cyan Blue) | `#FFF3E0` | Electricity, internet, bills |
| **Destructive / Alert** | `#D4183D` (Crimson) | `#FFEBEE` | Overbudget (>100%), Delete actions, Error state |
| **Warning / Caution** | `#D97706` (Amber) | `#FEF3C7` | Near budget limit (80%–100%), Uncategorized warning |
| **AI Insights Chip** | `#7B5CFF` (Purple) | `#EFE5FF` (`#D5C1FF` border)| Smart AI categorization, summary insights |
| **Date Selection** | `#66AFF0` (Sky Blue) | `#E1F5FE` | Calendar active day, multiselect toggle active |
| **Today Anchor** | `#C8A437` (Gold) | `#FFFDE7` | Quick "Today" jump pill |

---

## 3. Typography Hierarchy (Inter Typeface)

All typography is built on Google's **Inter** font family across 5 defined weights: Light (`300`), Regular (`400`), Medium (`500`), SemiBold (`600`), and Bold (`700`).

```kotlin
// Android Material 3 Typography Mapping (Type.kt)
val Typography = Typography(
    // 1. Hero Financial Spend Numbers
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    // 2. Section Headers / Date Groups
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // 3. Card Titles & Category Names
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    // 4. Standard Body & Notes
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    // 5. Captions, Timestamps & Micro-pills
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    )
)
```

---

## 4. Spacing & The 16dp Horizontal Rail Grid

To eliminate layout jitter and erratic eye scanning, **all screen content must align to a strict 16dp horizontal guide rail**.

```text
┌────────────────────────────────────────────────────────────┐  ◄ Screen Edge
│ ◄─ 16dp ─► [TopAppBar: Profile / Title / Search] ◄─ 16dp ─► │
│ ◄─ 16dp ─► [Hero Spend: ₹24,850.00             ] ◄─ 16dp ─► │
│ ◄─ 16dp ─► [Tactile Budget Bar (6dp Height)    ] ◄─ 16dp ─► │
│ ◄─ 16dp ─► [Stat Cards: Monthly / Daily Avg    ] ◄─ 16dp ─► │
│ ◄─ 16dp ─► [Date Navigation Header & Pills     ] ◄─ 16dp ─► │
│ ◄─ 16dp ─► [Category Filter Chips              ] ◄─ 16dp ─► │
│ ◄─ 16dp ─► [Transaction Card: Groceries        ] ◄─ 16dp ─► │
└────────────────────────────────────────────────────────────┘
```

### Spacing Tokens (`Density: 8/10`)

| Token | Dp Value | Primary Use Case |
| :--- | :--- | :--- |
| `SpaceMicro` | `2.dp` | Inline dot separators, badge padding |
| `SpaceTiny` | `4.dp` | Expense list item vertical gaps, icon-to-label spacing |
| `SpaceSmall` | `8.dp` | Card internal element gaps, button internal padding |
| `SpaceMedium` | `12.dp` | Sub-section spacing, card internal padding |
| `SpaceRail` | **`16.dp`** | **Screen horizontal padding (Outer Guide Rail)**, list margins |
| `SpaceSection`| `20.dp` | Spacing between major screen modules |
| `SpaceHero` | `24.dp` | Hero dashboard vertical breathing room |

---

## 5. Elevation, Shapes & Corner Radii

Never use arbitrary corner radii. Standardize shapes across components:

| Component Level | Corner Radius | Elevation (Light) | Border Treatment |
| :--- | :--- | :--- | :--- |
| **Pills & Badges** | `24.dp` (Full Pill) | `0.dp` | 1dp solid with 20% tint color |
| **Date Buttons** | `10.dp` | `0.dp` | 1dp solid `CardBorder` |
| **Stat / Chart Cards** | `14.dp` | `1.dp` (soft shadow)| 1dp solid `CardBorder` |
| **Transaction Cards** | `16.dp` | `0.5.dp` | 1dp solid `CardBorder` |
| **Bottom Sheets / Dialogs**| `24.dp` (Top) | `8.dp` | Hairline border |

---

## 6. Touch Targets & Mobile Fitts's Law

Following mobile UX standards (`references/pro-rules.md`):

1. **Minimum 44×44dp Touch Target**:
   * Checkbox circles, icon buttons, edit triggers, and date navigation pills must have a **44×44dp minimum hit area**, even if the visual icon is 20dp or 24dp.
   * Wrap micro-buttons in `Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center)`.
2. **Material 3 Ripple Indication**:
   * Every clickable element must provide instant visual feedback.
   * Use bounded ripples for cards/chips, unbounded ripples for icon-only action buttons.
3. **No Accidental Trigger Zones**:
   * Keep a minimum `8.dp` clearance between destructive actions (Delete) and navigation controls.

---

## 7. Data Visualization & Chart Standards

### A. Tactile Budget Progress Bar
* **Bar Height:** `6.dp` (crisp, tactile, modern)
* **Track Border:** Subtle `0.5.dp` border around the background track
* **Semantic 3-Stage Alert Logic:**
  * Spend `< 80%`: Normal brand green (`#439D46`)
  * Spend `80% – 100%`: Warning amber (`#D97706`)
  * Spend `> 100%`: Alert crimson (`#D4183D`) with subtle pulse

### B. Cumulative Monthly Spend Chart
* **Corner Radius:** `14.dp`
* **Grid Lines:** Muted alpha (`rgba(0,0,0,0.06f)`), dashed or hairline
* **Curve Rendering:** Cubic Bézier path smoothing with soft gradient fill below line
* **Tooltip Interaction:** Long-press / touch scrubber displaying date, amount, and category breakdown pill.

### C. Animated Pager Indicators
* Active indicator: Animated expanding pill (`16.dp` width × `6.dp` height) with smooth `animateDpAsState`.
* Inactive indicators: Circular dots (`6.dp` width × `6.dp` height) with 40% alpha.

---

## 8. Anti-Patterns (Forbidden in KharchaJi)

* ❌ **No Emojis as UI Action Icons**: Never use raw emojis (📊, 📈, 🏷️, ✨, 🌅) for buttons or primary navigation. Use Material vector icons (`Icons.Outlined.BarChart`, `Icons.Outlined.ReceiptLong`, `Icons.Outlined.AutoAwesome`) wrapped in soft circular badges.
* ❌ **No Jittering Margins**: Never mix 20dp on the header and 12dp on cards. Strictly adhere to the `16.dp` guide rail.
* ❌ **No Low-Contrast Text**: Faint grays or yellows with `<4.5:1` contrast ratio are forbidden. Always use `EggnogDark` or `TextPrimary`.
* ❌ **No 0ms Instant UI Changes**: Animate layout expands, amount changes (via `RollingNumber`), and state toggles using standard Compose springs (`150ms–250ms`).
* ❌ **No Unconstrained Recomposition**: Hoist state properly; wrap list filters and complex calculations in `derivedStateOf`.

---

## 9. Pre-Delivery Quality Checklist

Before committing any UI changes:
- [ ] 16dp horizontal rail maintained across TopBar, content, cards, and bottom actions.
- [ ] Contrast ratio ≥ 4.5:1 on all text labels.
- [ ] All interactive touch targets ≥ 44×44dp with ripple feedback.
- [ ] Status bar and navigation bar insets handled cleanly with edge-to-edge support.
- [ ] Vector icons used instead of raw emojis.
- [ ] High-density spacing (8dp scale) strictly applied.
- [ ] Dark Mode and Light Mode verified without text clipping or washed-out backgrounds.
