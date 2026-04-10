# My Car Companion — KMP

## What It Is
A Kotlin Multiplatform app (Android + iOS + Web) for managing vehicles, maintenance, and mechanic relationships. Car owners track vehicles, log maintenance, set reminders, and connect with mechanics. Mechanics manage their profile and assigned vehicles.

## Stack
- KMP shared module: Kotlin 2.2.10, Compose Multiplatform 1.7.3
- Navigation: Voyager 1.1.0-beta03 (single-navigator pattern — see architecture decisions)
- DI: Koin 4
- Backend: Supabase 3 (auth, database, RLS)
- AGP 9+ `com.android.kotlin.multiplatform.library` plugin

## Role System
- Roles stored in `user_roles` table: `admin`, `mechanic`, `owner`
- `profiles` table has `is_premium`, `is_mechanic_pro`, `subscription_tier`
- AppUser constructed in AuthRepository from auth state + role queries

## Core Constraints
- Never destroy the Voyager Navigator — auth transitions happen inside screens via LaunchedEffect, not at AppNavigation level
- All UI in shared KMP module — no Android/iOS-specific UI code
- Koin DI — repositories are singletons, ScreenModels are factories
- Supabase RLS enforces data access — repositories assume current user context
