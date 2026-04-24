# My Car Companion — Design System

## 1. Brand Identity
- **App Name**: My Car Companion
- **Tagline**: Your vehicle. Your history. Your mechanic.
- **Voice**: Trustworthy, professional, approachable. Not corporate. Not gimmicky.
- **Vibe**: Clean Pro with Modern Dark hints — navy authority with sky-blue warmth.

## 2. Color Palette

| Role | Name | Hex |
|------|------|-----|
| Primary Background | Deep Navy | `#1B2B50` |
| Surface / Card | Navy Lighter | `#243460` |
| Accent / Interactive | Sky Blue | `#6BBCDE` |
| Accent Hover | Sky Bright | `#8DCDE8` |
| Text Primary | White | `#FFFFFF` |
| Text Secondary | Muted White | `#A8BAD4` |
| CTA Button | Sky Blue | `#6BBCDE` |
| CTA Button Text | Deep Navy | `#1B2B50` |
| Success | Emerald | `#34C98A` |
| Warning | Amber | `#F5A623` |
| Border / Divider | Navy Border | `#2E3F6E` |

## 3. Typography

| Role | Font | Weight | Size |
|------|------|--------|------|
| Heading H1 | Inter | 700 Bold | 56px / 3.5rem |
| Heading H2 | Inter | 600 SemiBold | 36px / 2.25rem |
| Heading H3 | Inter | 600 SemiBold | 24px / 1.5rem |
| Body | Inter | 400 Regular | 16px / 1rem |
| Small / Caption | Inter | 400 Regular | 14px / 0.875rem |
| CTA Label | Inter | 600 SemiBold | 16px / 1rem |

Google Font: `https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap`

## 4. Shape & Spacing

- **Border Radius**: 12px cards, 8px buttons, 6px inputs
- **Shadow**: `0 4px 24px rgba(0,0,0,0.3)` on cards
- **Section Padding**: 80px vertical on desktop, 48px on mobile
- **Container Max Width**: 1200px, centered

## 5. Components

### Primary Button (CTA)
```
background: #6BBCDE
color: #1B2B50
border-radius: 8px
padding: 14px 28px
font-weight: 600
hover: background #8DCDE8, slight lift shadow
```

### Secondary Button (Outline)
```
background: transparent
border: 2px solid #6BBCDE
color: #6BBCDE
border-radius: 8px
padding: 12px 26px
hover: background rgba(107,188,222,0.1)
```

### Feature Card
```
background: #243460
border: 1px solid #2E3F6E
border-radius: 12px
padding: 28px
shadow: 0 4px 24px rgba(0,0,0,0.3)
icon: Sky Blue #6BBCDE, 32px
```

### Navigation Bar
```
background: rgba(27,43,80,0.95)
backdrop-filter: blur(12px)
position: sticky top
border-bottom: 1px solid #2E3F6E
logo: car icon + "My Car Companion" text in white
links: Inter 500, #A8BAD4, hover #FFFFFF
CTA button: Primary Button style, smaller (padding 10px 20px)
```

## 6. Design System Notes for Stitch Generation

Copy this block into every Stitch prompt:

```
**DESIGN SYSTEM (REQUIRED):**
- Platform: Web, Desktop-first (responsive down to mobile)
- Palette: Deep Navy (#1B2B50) as background, Sky Blue (#6BBCDE) as primary accent and CTA, Navy Lighter (#243460) for cards/surfaces, White (#FFFFFF) for headings, Muted White (#A8BAD4) for body text
- Typography: Inter font family; 700 for H1, 600 for H2/H3, 400 for body
- Styles: 12px rounded cards, 8px rounded buttons, glassmorphism sticky nav, subtle card shadows (0 4px 24px rgba(0,0,0,0.3))
- Atmosphere: Clean professional dark-navy SaaS site. Automotive industry. Trustworthy and modern. Not flashy.
```

## 7. Logo Usage
- Logo: Rounded-square app icon with deep navy background and sky-blue car illustration
- On dark backgrounds: use full-color logo
- Text lockup: "My Car Companion" in white, Inter 600
- Minimum size: 32px height
- Clear space: half the logo height on all sides
