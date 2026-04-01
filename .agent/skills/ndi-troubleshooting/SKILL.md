---
name: ndi-troubleshooting
description: Filtra y analiza los logs de la aplicación para detectar problemas de señal NDI o crashes.
---

# Skill: NDI Troubleshooting

Este skill ayuda a identificar problemas de conectividad o de rendimiento en el NDI TV Player.

## Scripts Incluidos
- **`filter_ndi_logs.ps1`**: Extrae logs específicos de la aplicación desde un dispositivo conectado por ADB.

## Modos de Uso

### Filtrar Logs con ADB
```powershell
adb logcat | Select-String -Pattern "NDI_JNI|NDI_Debug|PlayerActivity"
```

### Problemas Comunes

1. **Pantalla Negra**:
   - Falta de señal en la red (WiFi deshabilitado o red diferente).
   - El equipo NDI de origen no está emitiendo.
2. **Lag o Tirones**:
   - Verificar si el equipo soporta la resolución actual. (Pruebe con la rama `perf-optimization` si es un equipo débil).
   - Compruebe la congestión de la red WiFi.
3. **Fallo en el Descubrimiento**:
   - Asegúrese de que el `MulticastLock` está activo (log: "Multicast lock acquired").
   - Compruebe los permisos `NEARBY_WIFI_DEVICES`.
