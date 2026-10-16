---
id: review
title: Code Review
triggers: [review, pr, pull request, check, audit, inspect, verify]
packages: []
complexity: medium
---

# Code Review Skill

What to check when reviewing PRs or auditing code.

## When to Use

- Reviewing pull requests
- Auditing code for issues
- Pre-commit self-review
- Security review

## Review Checklist

### Build & Compile

- [ ] Compiles at source level 1.3 (no generics, lambdas)
- [ ] No `java.util` collections (use arrays/Vector)
- [ ] No `String.split()` (use `Str.split()`)
- [ ] No `java.security` (use custom crypto)
- [ ] No reflection
- [ ] No floating point (CLDC 1.1)
- [ ] ProGuard config updated if needed

### Testing

- [ ] All existing tests pass (`ant test`)
- [ ] New tests added for new features
- [ ] Edge cases covered (null, empty, max values)
- [ ] No test dependencies on external state
- [ ] Tests are deterministic

### Security

- [ ] All IRC input sanitized via `IrcSanitizer`
- [ ] No passwords in plain text (use `PasswordObfuscator`)
- [ ] No nonce reuse in crypto
- [ ] Constant-time MAC comparison
- [ ] No sensitive data in logs
- [ ] Certificate pinning for TLS

### Code Quality

- [ ] Follows naming conventions (PascalCase, camelCase, UPPER_SNAKE)
- [ ] 4-space indent, no tabs
- [ ] Braces on new line
- [ ] Javadoc for public APIs
- [ ] Inline comments for non-obvious logic
- [ ] No debug logging left in
- [ ] No commented-out code

### Architecture

- [ ] Respects package boundaries
- [ ] `proto/` has zero MIDP deps
- [ ] `crypto/` has zero deps
- [ ] `ui/` depends on core, not vice versa
- [ ] No circular dependencies

### Documentation

- [ ] Docs updated for user-facing changes
- [ ] CHANGELOG.md updated
- [ ] Code comments added for complex logic
- [ ] Spanish docs updated (if applicable)

### Performance

- [ ] No object creation in paint()
- [ ] No String concatenation in loops
- [ ] RMS writes batched
- [ ] Memory usage reasonable
- [ ] No memory leaks

## Review Decision Tree

```
PR submitted
├── Does it compile?
│   ├── NO → Request changes
│   └── YES → Continue
├── Do tests pass?
│   ├── NO → Request changes
│   └── YES → Continue
├── Are new tests added?
│   ├── NO → Request changes
│   └── YES → Continue
├── Security review
│   ├── Input sanitized?
│   ├── Passwords obfuscated?
│   └── No crypto issues?
├── Code quality
│   ├── Follows conventions?
│   ├── Well documented?
│   └── No debug code?
└── Approve or request changes
```

## Common Issues to Flag

### Build Issues

| Issue | Severity | Message |
|-------|----------|---------|
| Uses generics | **Blocker** | "CLDC 1.1 doesn't support generics" |
| Uses String.split() | **Blocker** | "Use Str.split() instead" |
| Uses java.security | **Blocker** | "Use custom crypto classes" |
| Uses floating point | **Blocker** | "CLDC 1.1 doesn't support float" |

### Security Issues

| Issue | Severity | Message |
|-------|----------|---------|
| Unsanitized input | **Critical** | "Sanitize with IrcSanitizer" |
| Plain text password | **Critical** | "Use PasswordObfuscator" |
| Nonce reuse | **Critical** | "Generate new nonce each time" |
| Timing attack | **High** | "Use constant-time comparison" |
| Sensitive data in logs | **High** | "Remove password from log" |

### Code Quality Issues

| Issue | Severity | Message |
|-------|----------|---------|
| Debug logging left | **Medium** | "Remove System.out.println" |
| Missing Javadoc | **Low** | "Add Javadoc for public API" |
| Commented-out code | **Low** | "Remove dead code" |
| Magic numbers | **Low** | "Use named constant" |

## Review Template

```markdown
## Review Summary
Brief description of changes.

## Build & Tests
- [ ] Compiles at source level 1.3
- [ ] All tests pass
- [ ] New tests added

## Security
- [ ] Input sanitized
- [ ] No plain text passwords
- [ ] No crypto issues

## Code Quality
- [ ] Follows conventions
- [ ] Well documented
- [ ] No debug code

## Issues Found
1. [Critical] Description
2. [Medium] Description

## Verdict
- [ ] Approve
- [ ] Request changes
- [ ] Comment
```

## Anti-Patterns

```java
// WRONG: Reviewer approves without checking
// "Looks good to me" → Merge

// RIGHT: Systematic review
// Check each item in checklist
// Flag issues with severity
// Request changes if needed
```

```java
// WRONG: Reviewer focuses only on style
// "Fix indentation" → but misses security issue

// RIGHT: Prioritize issues
// Critical > High > Medium > Low
// Security > Build > Quality > Style
```
