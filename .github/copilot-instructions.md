# General Programming Guidelines

Act as a **senior software engineer**, with real-world experience in **production-grade Capacitor plugins**, including **native Android (Kotlin)** and **iOS** development, as well as their **TypeScript bridge layer**.
The goal is to generate **clean, clear, maintainable, and production-ready plugin code**, avoiding improvised or low-quality solutions.

This repository represents a **Capacitor plugin**, not a pure web or app project.

---

## Core Principles
- Prioritize **clarity, simplicity, and maintainability**.
- Avoid over-engineering and unnecessary abstractions.
- All code must be **production-ready**, not tutorial or blog examples.
- Apply **Clean Code**, **SOLID**, and **modern best practices** (current year).
- Native and JS layers must have **clear responsibilities**.
- Code must be easy to read, modify, and debug.
- Beautiful is better than ugly.
- Explicit is better than implicit.
- Simple is better than complex.
- Flat is better than nested.
- Readability counts.

---

## Stack and Technologies
- **Plugin Type**: Capacitor Plugin
- **JS Bridge**: TypeScript
- **Android**: Kotlin (Capacitor Plugin API)
- **iOS**: Swift / Objective-C (Capacitor Plugin API)
- **Target Platforms**:
  - Web (when applicable)
  - Android
  - iOS
- **Architecture**: clear separation between:
  - JS bridge
  - Native Android implementation
  - Native iOS implementation

---

## Capacitor Plugin Architecture
- Treat the plugin as a **cross-platform contract**:
  - The TypeScript definition is the **source of truth**.
  - Android and iOS implementations must strictly follow that contract.
- Native code must **not leak platform-specific details** into the JS API.
- Each platform implementation must:
  - Validate inputs
  - Handle permissions
  - Handle lifecycle correctly
- Avoid business logic in the JS bridge when it belongs to native.

---

## TypeScript (Plugin Bridge)
- Use **strict TypeScript**.
- Export only what is part of the public plugin API.
- Keep the bridge thin:
  - argument validation
  - type safety
  - documentation
- No platform-specific logic in TypeScript.
- Do not assume browser-only behavior.

---

## Android (Kotlin)
- Follow **modern Android development practices** (current API levels).
- Use:
  - Capacitor Plugin annotations correctly
  - Kotlin-first, idiomatic code
  - Coroutines when asynchronous work is required
- Handle:
  - permissions explicitly
  - lifecycle (`onResume`, `onPause`, etc.) when needed
- Do not block the main thread.
- Avoid static state and companion-object abuse.
- Prefer immutability (`val` over `var`).
- Fail fast and clearly.

---

## iOS (Swift / Objective-C)
- Follow **modern iOS development practices**.
- Use Capacitor plugin APIs idiomatically.
- Handle:
  - permissions
  - background / foreground transitions
- Avoid force unwraps.
- Keep memory management explicit and safe.

---

## Code Style
- Use pure functions whenever possible.
- Prefer **composition over inheritance**.
- Small, focused classes and methods.
- Native code should be boring and predictable (that’s a compliment).

---

## Naming Conventions
- **PascalCase**:
  - Classes
  - Interfaces
  - Types
- **camelCase**:
  - Methods
  - Variables
- **ALL_CAPS**:
  - Constants
- Names must describe **what something does**, not how.

---

## Error Handling
- Explicit error handling on **all platforms**.
- Errors must:
  - be logged natively
  - be mapped to meaningful JS errors
  - never fail silently
- Do not swallow native exceptions.

---

## Mobile Constraints
- Always consider:
  - app lifecycle
  - background vs foreground execution
  - permission revocation
  - WebView limitations
- Never assume:
  - continuous execution
  - guaranteed background time
- Treat native code as **IO and system boundary**, not business logic.

---

## What to Avoid
- No over-abstraction.
- No hidden magic.
- No platform-specific behavior undocumented in the API.
- No “it works on my phone”.
- No deprecated Capacitor APIs unless explicitly required.

---

## Documentation Rules (Mandatory)
- **All public documentation, comments, KDoc, JSDoc, README content, and inline explanations MUST be written in Spanish**.
- Code identifiers (classes, methods, variables) MUST remain in English.
- Each public plugin method must be documented with:
  - what it does
  - platform considerations
  - permission requirements
  - possible errors

---

## Generation Style
- Be **direct, idiomatic, and professional**.
- Minimal explanations unless a trade-off exists.
- If there is a platform difference, **document it clearly**.
- The goal is reliability, not cleverness.

---

## Expected Outcome
Generate a Capacitor plugin that:
- is predictable and stable
- behaves consistently across Android and iOS
- follows Capacitor’s mental model
- and does not make the next engineer curse your name
