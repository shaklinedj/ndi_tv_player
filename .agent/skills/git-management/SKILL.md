---
name: git-management
description: Gestión de ramas y flujo de trabajo Git para este proyecto.
---

# Skill: Git & Branching Management

Garantiza un flujo de trabajo ordenado para el desarrollo de NDI TV Player.

## Flujo de Trabajo

1. **Nueva Función**: Cree una rama descriptiva.
   ```powershell
   git checkout -b feature-nueva-funcionalidad
   ```
2. **Merge a Master**: Siempre que se complete una función importante y se verifique.
   ```powershell
   git checkout master
   git merge feature-nueva-funcionalidad
   ```
3. **Pilar del Proyecto**: La rama `master` siempre debe ser la versión estable.

## Ramas Recientes
- `perf-optimization`: Rama experimental donde se mejoró notablemente la fluidez en equipos de bajos recursos. (Fijada en `master` el 21-03-2026).
- `loading-indicator`: Implementó la pantalla de "Conectando..." en lugar de pantalla negra. (Fijada en `master` el 21-03-2026).
