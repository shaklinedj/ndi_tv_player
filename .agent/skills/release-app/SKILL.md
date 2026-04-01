---
name: release-app
description: Compila y sube la aplicación a Firebase App Distribution de forma automatizada.
---

# Skill: Release App to Firebase

Este skill automatiza el proceso de compilación y despliegue de la aplicación NDI TV Player.

## Requisitos
- **Android Studio JBR**: Se debe configurar `JAVA_HOME` apuntando al JRE de Android Studio.
- **Gradle**: El proyecto utiliza `gradlew.bat` en Windows.

## Instrucciones de Uso

1. **Incrementar Versión**: Asegúrate de actualizar el `versionCode` y `versionName` en `app/build.gradle.kts`.
2. **JAVA_HOME**: Configura la variable de entorno:
   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   ```
3. **Compilar y Subir**:
   ```powershell
   .\gradlew.bat clean assembleRelease appDistributionUploadRelease
   ```

## Parámetros de Configuración
- `groups`: Configurado actualmente como "testers" en el bloque `firebaseAppDistribution`.
