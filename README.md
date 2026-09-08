# IDOban — Ultra-Studio

Tablero personal Kanban (tipo Trello) 100% para proyectos propios. Web primero, móvil después (1:1 Ultra-Studio).

**Stack:** HTML/CSS/JS vanilla, localStorage offline-first. Sin backend.
**Estética:** VOCAL ULTRA-STUDIO — `#f8f9fa` canvas, `#121212`/`#0a0a0a` displays, `#d32f2f` activo, `#e2e8f0` inactivo, `Courier New` mono, knobs con espiral.

## Estructura
```
IDOban/
 ├─ index.html   — App principal (Categoría > Proyecto > Kanban 3 cols)
 ├─ styles.css   — Sistema Ultra-Studio + responsive 375/768/1440
 ├─ app.js       — Lógica, drag&drop touch/desktop, localStorage
 ├─ widget.html  — Preview del widget de home del celular (no dentro de la app)
 └─ manifest.json— PWA standalone
```

## Flujo
1. Categorías (áreas grandes creadas por ti: Universidad, Trabajo…)
2. Proyectos (dentro de categoría, con barra % Sin/Proc/Term)
3. Tareas minimalistas (solo título, max 80) arrastradas entre SIN COMENZAR / EN PROCESO / TERMINADO

## Widget home
No vive en la app. `widget.html` simula el widget Android 4×2: lee `idoban_recent_v1` (últimos 4 proyectos vistos), muestra título + `us-progress` segmentado, tap → `index.html#project=ID` (deep-link `idoban://project/{id}` en port nativo).

## Uso
```bash
# desde IDOcod
python -m http.server 5174
# abrir
http://localhost:5174/IDOban/          # app
http://localhost:5174/IDOban/widget.html # preview widget home
```

## Datos
- `idoban_board_v1` → `{categories:[{id,name,createdAt}], projects:[{id,categoryId,name,createdAt}], tasks:[{id,projectId,title,status,createdAt}]}`
- `idoban_recent_v1` → `[projectId, ...]` (max 4)

## Port a móvil
Mismo `styles.css`/`app.js` sin rediseño. Opciones: Capacitor (WebView + AppWidgetProvider), React Native, o Kotlin/Glance leyendo el mismo `localStorage` (o puente nativo).
