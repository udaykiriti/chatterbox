# Inicio Rápido con Chatterbox

Esta guía te lleva a tener Chatterbox funcionando en tu Nokia en el menor tiempo posible.

---

##  Prerrequisitos

### En tu Computadora
- **Java JDK 8–21** — para compilar
- **Apache Ant 1.10+** — orquestación de build
- **Sun WTK 2.5.2** o **Nokia S60 SDK** — para `preverify` y emulador (requerido para JAR de dispositivo)
- **ProGuard 7.x+** — opcional pero recomendado para optimización de tamaño

### En tu Teléfono
- Nokia S60 3rd/5th Ed. o S40 3rd/5th Ed. con MIDP 2.0
- ~500 KB almacenamiento libre (JAR + JAD + RMS)
- Conexión Wi-Fi o 2G/3G

---

##  Inicio en 5 Minutos

### 1. Clona y Compila
```bash
git clone https://github.com/tu-repo/chatterbox.git
cd chatterbox

# Configura WTK (ajusta para tu sistema)
export WTK_HOME=/opt/WTK2.5.2

# Compila JAR + JAD de release
ant dist
```

**Salida:** `dist/chatterbox.jar` + `dist/chatterbox.jad`

### 2. Transfiere al Teléfono (Elige uno)

| Método | Pasos |
|--------|-------|
| **Bluetooth** | Empareja teléfono  PC → Envía ambos archivos via OBEX → Abre desde Bandeja de entrada |
| **Tarjeta de Memoria** | Copia a `E:/Others/` o `mass_memory/` → Abre en Gestor de Archivos |
| **OTA (Web)** | Sube archivos a servidor web con MIME types correctos → Abre URL del JAD en navegador |

### 3. Configuración Inicial

Al primer lanzamiento, la pantalla **Configuración** aparece automáticamente. Rellena:

```
Servidor:     irc.libera.chat
Puerto:       6697
Usar TLS:      (marcado)
Nick:         tunick
Alt nick:     tunick_
Canales:      #canal1,#canal2
Nombre real:  Usuario Chatterbox
```

**Opcional — Gateway (recomendado para problemas TLS):**
```
Gateway host: tu.servidor.ip
Gateway port: 6667
Gateway pass: tu-secreto-compartido
```

Pulsa **Opciones → Guardar**, luego **Opciones → Conectar**.

---

##  Flujo de Conexión

```
┌─────────────┐     ssl://:6697     ┌──────────────┐
│  Chatterbox │ ──────────────────► │ irc.libera.chat │
│   (Teléfono)│   (TLS Directo)     │   (Red IRC)    │
└─────────────┘                     └──────────────┘
       │
       │ ssl:// falla (certificado)
       ▼
┌─────────────┐     socket://:6667   ┌──────────────┐     TLS      ┌──────────────┐
│  Chatterbox │ ──────────────────► │   Gateway    │ ───────────► │ irc.libera.chat │
│   (Teléfono)│   (TCP Plano)       │  (Tu VPS)    │              │   (Red IRC)    │
└─────────────┘                     └──────────────┘              └──────────────┘
```

El MIDlet **intenta TLS directo primero**. Solo si falla, usa el gateway.

---

##  Verifica que Funciona

1. **Conectado** — Estado muestra "Conectado" + bienvenida del servidor (numérico 001)
2. **Auto-unido** — Canales de configuración aparecen con listas de nicks
3. **Envía mensaje** — Pulsa `*` (Star) o tecla Verde para enviar
4. **Recibe mensaje** — Pide que te hagan ping; badge aparece en pestaña del canal
5. **Completado de nick** — Escribe primeras letras de un nick + `Tab` para ciclar

---

## 🆘 Si Algo Falla

| Síntoma | Causa Probable | Solución |
|---------|----------------|----------|
| "Error de seguridad" al conectar | TLS certificado bloqueado | Usa gateway (ver arriba) |
| "Conexión rechazada" | Host/puerto incorrecto o firewall | Verifica servidor:puerto, prueba 6667 plano |
| "JAD inválido" | MIME types incorrectos (OTA) | Servidor debe enviar `text/vnd.sun.j2me.app-descriptor` |
| App no instala | Self-signed en S60 | Firma con cert dev o usa gateway |
| No canales unidos | Campo canales vacío | Añade canales separados por comas en Configuración |

Ver [Solución de Problemas](troubleshooting.md) para más.

---

##  Próximos Pasos

- Aprende el [Mapa de Teclas](usage.md#mapa-de-teclas-nokia-e63e71e72c3)
- Domina los [Comandos](usage.md#referencia-de-comandos)
- Configura tu propio [Gateway](gateway.md) para TLS fiable
- Lee [Configuración](configuration.md) para opciones avanzadas

---

##  Comunidad

- **IRC:** `#chatterbox` en Libera.Chat
- **Issues:** GitHub Issues
- **Wiki:** Esta documentación

*¡Feliz chat en tu Nokia clásico!* 