---
id: localization
title: Localization
triggers: [localization, translation, spanish, docs_es, i18n, l10n, language]
packages: [docs]
complexity: easy
---

# Localization Skill

Managing Spanish translations and documentation localization.

## When to Use

- Adding new documentation pages
- Updating existing docs
- Translating content
- Keeping translations in sync

## Current Status

| Language | Location | Status |
|----------|----------|--------|
| English | `docs/` | Primary |
| Spanish | `docs_es/` | Partial (2 files) |

## File Structure

```
docs/                    # English (primary)
├── index.md
├── getting-started.md
├── building.md
├── installation.md
├── configuration.md
├── gateway.md
├── usage.md
├── architecture.md
├── development.md
├── troubleshooting.md
├── security.md
└── RUNNING.md

docs_es/                 # Spanish (translations)
├── index.md
└── getting-started.md
```

## Translation Workflow

### Adding a New Page

1. Create English version in `docs/`
2. Create Spanish version in `docs_es/`
3. Keep structure parallel
4. Update `mkdocs.yml` if using localization plugin

### Updating Existing Pages

1. Update English version in `docs/`
2. Update Spanish version in `docs_es/`
3. Keep content in sync
4. Note: Spanish docs may lag behind English

## Translation Guidelines

### Do
- Translate meaning, not word-for-word
- Keep technical terms in English when appropriate
- Maintain consistent terminology
- Keep code examples unchanged
- Update both languages when adding features

### Don't
- Leave untranslated placeholders
- Translate code or commands
- Change technical terms inconsistently
- Forget to update navigation

## Translation Status Tracking

| English File | Spanish File | Status |
|-------------|--------------|--------|
| `docs/index.md` | `docs_es/index.md` | Translated |
| `docs/getting-started.md` | `docs_es/getting-started.md` | Translated |
| `docs/building.md` | — | Not started |
| `docs/installation.md` | — | Not started |
| `docs/configuration.md` | — | Not started |
| `docs/gateway.md` | — | Not started |
| `docs/usage.md` | — | Not started |
| `docs/architecture.md` | — | Not started |
| `docs/development.md` | — | Not started |
| `docs/troubleshooting.md` | — | Not started |
| `docs/security.md` | — | Not started |

## Common Translations

| English | Spanish |
|---------|---------|
| Build | Compilar |
| Install | Instalar |
| Configuration | Configuración |
| Gateway | Puerta de enlace / Gateway |
| Channel | Canal |
| Nickname | Apodo / Nick |
| Server | Servidor |
| Connection | Conexión |
| Message | Mensaje |
| Settings | Ajustes |
| Documentation | Documentación |

## Anti-Patterns

```markdown
<!-- WRONG: Mixing languages -->
# Bienvenido a Chatterbox
This is the IRC client for Nokia phones.

<!-- RIGHT: Consistent language -->
# Bienvenido a Chatterbox
Este es el cliente IRC para teléfonos Nokia.
```

```markdown
<!-- WRONG: Translating code blocks -->
```bash
compilar  # Wrong! Don't translate commands
```

<!-- RIGHT: Keep code unchanged -->
```bash
ant dist  # Commands stay in English
```
```

## Localization Checklist

- [ ] English version created/updated
- [ ] Spanish version created/updated
- [ ] Navigation updated
- [ ] Code examples unchanged
- [ ] Technical terms consistent
- [ ] Links work in both languages
- [ ] Docs build passes (`mkdocs build --strict`)
