# Chatterbox Documentación

Bienvenido a la documentación completa de **Chatterbox** — un cliente IRC listo para producción para teléfonos Nokia QWERTY clásicos (E63, E71, E72, C3, etc.) que ejecutan Symbian S60 3rd/5th Edition o Series 40 3rd/5th Edition.

##  Índice de Documentación

| Documento | Descripción |
|-----------|-------------|
| [Inicio Rápido](getting-started.md) | Guía de inicio rápido para nuevos usuarios |
| [Compilación](building.md) | Instrucciones completas de compilación |
| [Instalación](installation.md) | Métodos para instalar en tu Nokia |
| [Configuración](configuration.md) | Configuración detallada, perfiles y gateway |
| [Servidor Gateway](gateway.md) | Configuración del servidor de relay TLS |
| [Uso y Comandos](usage.md) | Referencia completa de comandos y teclas |
| [Arquitectura](architecture.md) | Estructura del código, patrones y módulos |
| [Desarrollo](development.md) | Testing, debugging y contribución |
| [Solución de Problemas](troubleshooting.md) | Problemas comunes y soluciones |
| [Seguridad](security.md) | Modelo de amenazas, TLS, certificados |

---

##  Enlaces Rápidos

- **README Principal**: [../README.md](../README.md)
- **Código Fuente**: [../midlet/src/](../midlet/src/)
- **Servidor Gateway**: [../gateway/](../gateway/)
- **Suite de Tests**: [../test/](../test/)
- **Script de Build**: [../build.xml](../build.xml)

---

##  Dispositivos Soportados

| Serie | Modelos | Notas |
|-------|---------|-------|
| **S60 3rd Ed.** | E63, E71, E72, E90, N82, N95, 5700, 6110, 6120, 6220, N73, N76, N81, N93 | Soporte completo |
| **S60 5th Ed.** | 5800, N97, N97 mini, X6, C6, 5230, 5233, 5250, 5530 | Touch + teclado |
| **S40 3rd Ed.** | 6131, 6233, 6234, 6300, 6500, 7370, 7373, 7390, 7500, 7900 | Teléfono básico |
| **S40 5th Ed.** | C3-00, C3-01, X3-02, C5-00.2, C6-00, E5-00, E7-00 | QWERTY candybar |

> **Requisitos mínimos:** MIDP 2.0, CLDC 1.1, ~300 KB heap libre, red TCP/IP

---

##  Características Principales

- **Múltiples perfiles** de servidor almacenados en RMS (persistentes)
- **TLS primero** con fallback automático a plano via gateway
- **Soporte completo RFC 2812** protocolo IRC
- **Buffers circulares** por canal (200 líneas de scrollback)
- **Listas de nicks** con modos (@ + % & ~ !)
- **Badges de highlight** + vibración/beep en mención/PM
- **Completado de nick** con Tab en campo de entrada
- **197 tests unitarios** pasando (protocolo, core, sesión)
- **JAR < 300 KB** tras optimización ProGuard

---

##  Empieza Aquí

1. **¿Nuevo en Chatterbox?** → [Inicio Rápido](getting-started.md)
2. **¿Quieres compilarlo?** → [Compilación](building.md)
3. **¿Instalar en el teléfono?** → [Instalación](installation.md)
4. **¿Configurar gateway?** → [Servidor Gateway](gateway.md)
5. **¿Aprender comandos?** → [Uso y Comandos](usage.md)

---

##  Licencia

Licencia MIT — ver archivo [LICENSE](../LICENSE).

*Construido con  por el amor a los teclados físicos y IRC.*