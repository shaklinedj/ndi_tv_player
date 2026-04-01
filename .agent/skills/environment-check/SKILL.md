---
name: environment-check
description: Verificación rápida del entorno de desarrollo (Android SDK, NDK, Java, ADB).
---

# Skill: Development Environment Check

Este skill permite validar rápidamente que el entorno local tiene todo lo necesario para compilar el proyecto NDI TV Player.

## Lista de Verificación (Checklist)

- [ ] **Android SDK**: `sdk.dir` en `local.properties` debe ser válido.
- [ ] **NDK**: Debe tener instalado la versión de NDK mencionada en `app/build.gradle.kts`.
- [ ] **Java 17 (o superior de Android Studio)**: La variable `JAVA_HOME` debe ser establecida antes de usar `gradlew`.
- [ ] **Dispositivo ADB**: Use `adb devices` para verificar que el TV Box o celular es visible.
- [ ] **NDI 6 SDK**: La carpeta `NDI 6 SDK (Android)` debe estar presente en la raíz del proyecto para suministrar los headers.

## Script de Validación
Consulte si `NDI 6 SDK` está presente:
```powershell
Test-Path -Path '..\NDI 6 SDK (Android)'
```
