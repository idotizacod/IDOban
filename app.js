// IDOban — Ultra-Studio — Vanilla JS — offline-first — con widget recientes + móvil 1:1
const LS_KEY = 'idoban_board_v1';
const LS_LEGACY_KEY = 'idocod_board_v1';
const LS_RECENT_KEY = 'idoban_recent_v1';
const MAX_RECENT = 6;

const defaultState = () => ({ categories: [], projects: [], tasks: [] });

function uid(){ return Math.random().toString(36).slice(2,9) + Date.now().toString(36).slice(-4); }

let state = defaultState();
let nav = { categoryId: null, projectId: null };
let modalMode = null; // 'category' | 'project' | 'editCategory' | 'editProject'
let editId = null;
let dragTaskId = null;

// Elements
const els = {
  catsGrid: document.getElementById('categoriesGrid'),
  emptyCats: document.getElementById('emptyCats'),
  projsGrid: document.getElementById('projectsGrid'),
  emptyProjs: document.getElementById('emptyProjs'),
  breadcrumb: document.getElementById('breadcrumb'),
  viewCats: document.getElementById('view-categories'),
  viewProjs: document.getElementById('view-projects'),
  viewBoard: document.getElementById('view-board'),
  displayCats: document.getElementById('displayCats'),
  displayProjs: document.getElementById('displayProjs'),
  displayTasks: document.getElementById('displayTasks'),
  projectsTitle: document.getElementById('projectsTitle'),
  projectsHint: document.getElementById('projectsHint'),
  boardTitle: document.getElementById('boardTitle'),
  boardStats: document.getElementById('boardStats'),
  colTodo: document.getElementById('colTodo'),
  colDoing: document.getElementById('colDoing'),
  colDone: document.getElementById('colDone'),
  countTodo: document.getElementById('countTodo'),
  countDoing: document.getElementById('countDoing'),
  countDone: document.getElementById('countDone'),
  inputTask: document.getElementById('inputTask'),
  modalOverlay: document.getElementById('modalOverlay'),
  modalTitle: document.getElementById('modalTitle'),
  modalInput: document.getElementById('modalInput'),
  modalError: document.getElementById('modalError'),
  // widget home es externo (widget.html), no dentro de la app
  mobileNav: document.getElementById('mobileNav'),
};

async function loadState(){
  try{
    let raw = localStorage.getItem(LS_KEY);
    if(raw){ try{ const p=JSON.parse(raw); if(isValidBoard(p)) return p; }catch{} }
    // Fallback 1: preferencias nativas (Capacitor Preferences / SharedPreferences) —
    // sobreviven a WebView limpio, cierre forzado o reinstalación sin respaldo.
    const nativeRaw = await getNativePrefsAsync(LS_KEY);
    if(nativeRaw){
      try{ const p=JSON.parse(nativeRaw); if(isValidBoard(p)){ localStorage.setItem(LS_KEY, JSON.stringify(p)); return p; } }catch{}
    }
    // Fallback 2: migración desde clave legacy IDOcod
    const legacy = localStorage.getItem(LS_LEGACY_KEY);
    if(legacy){
      try{ const p=JSON.parse(legacy); if(isValidBoard(p)){ localStorage.setItem(LS_KEY, legacy); return p; } }catch{}
    }
  }catch{}
  // Nunca se fabrica data demo: siempre se arranca con el tablero real (vacío si no hay nada).
  return defaultState();
}
function isValidBoard(p){
  return !!p && Array.isArray(p.categories) && Array.isArray(p.projects) && Array.isArray(p.tasks);
}
function getNativePrefs(key){
  try{
    if(window.AndroidBridge && window.AndroidBridge.getPrefs){
      const v = window.AndroidBridge.getPrefs(key);
      if(typeof v === 'string') return v;
    }
  }catch{}
  return null;
}
async function getNativePrefsAsync(key){
  try{
    if(window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Preferences){
      const r = await window.Capacitor.Plugins.Preferences.get({key});
      if(r && typeof r.value === 'string'){ localStorage.setItem(key, r.value); return r.value; }
    }
  }catch{}
  return getNativePrefs(key);
}
function setNativePrefs(key, value){
  try{
    if(window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Preferences){
      window.Capacitor.Plugins.Preferences.set({key, value}).catch(()=>{});
    }
    if(window.AndroidBridge && window.AndroidBridge.setPrefs){
      window.AndroidBridge.setPrefs(key, value);
    }
  }catch{}
}
function saveState(){
  const json = JSON.stringify(state);
  localStorage.setItem(LS_KEY, json);
  // Espejo nativo: el tablero sobrevive aunque el WebView pierda su storage.
  setNativePrefs(LS_KEY, json);
}

function setView(which){
  els.viewCats.classList.toggle('is-active', which==='cats');
  els.viewProjs.classList.toggle('is-active', which==='projs');
  els.viewBoard.classList.toggle('is-active', which==='board');
}

function getRecentIds(){
  try{ const r=JSON.parse(localStorage.getItem(LS_RECENT_KEY)||'[]'); return Array.isArray(r)?r:[]; }catch{ return []; }
}
function pushRecent(projectId){
  let arr=getRecentIds().filter(id=>id!==projectId);
  arr.unshift(projectId);
  arr=arr.slice(0,MAX_RECENT);
  const json=JSON.stringify(arr);
  localStorage.setItem(LS_RECENT_KEY, json);
  setNativePrefs(LS_RECENT_KEY, json);
}
function openProjectDirect(projectId){
  const proj=state.projects.find(p=>p.id===projectId);
  if(!proj) return;
  nav.categoryId=proj.categoryId;
  nav.projectId=proj.id;
  pushRecent(proj.id);
  renderAll();
  window.scrollTo({top:0, behavior:'smooth'});
}

function renderWidget(){
  // Widget es externo (widget.html) para home del celular — no se renderiza dentro de IDOban
  // Se mantiene el tracking de recientes para que el AppWidget lo lea vía idoban_recent_v1
  return;
}

function renderAll(){
  renderDisplays();
  renderBreadcrumb();
  renderWidget();
  // mobile nav visibility y estado
  if(els.mobileNav){
    const isHome=!nav.categoryId && !nav.projectId;
    els.mobileNav.style.display = ''; // css controla
    els.mobileNav.querySelectorAll('[data-mnav]').forEach(b=>{
      const isRecent=b.dataset.mnav==='recent';
      const active = isHome ? !isRecent : false;
      // en home, recent y cats son el mismo view pero widget scroll
      b.classList.toggle('is-active', isHome ? (b.dataset.mnav==='cats') : false);
    });
  }
  if(!nav.categoryId && !nav.projectId){ setView('cats'); renderCategories(); }
  else if(nav.categoryId && !nav.projectId){ setView('projs'); renderProjects(); }
  else if(nav.projectId){ setView('board'); renderBoard(); }
}

function renderDisplays(){
  els.displayCats.textContent = String(state.categories.length).padStart(2,'0');
  const projsCount = nav.categoryId ? state.projects.filter(p=>p.categoryId===nav.categoryId).length : state.projects.length;
  els.displayProjs.textContent = String(projsCount).padStart(2,'0');
  const tasksCount = nav.projectId ? state.tasks.filter(t=>t.projectId===nav.projectId).length : state.tasks.length;
  els.displayTasks.textContent = String(tasksCount).padStart(2,'0');
}

function renderBreadcrumb(){
  const parts = [];
  parts.push(`<a data-nav="home">CATEGORIAS</a>`);
  if(nav.categoryId){
    const cat = state.categories.find(c=>c.id===nav.categoryId);
    if(cat) parts.push(`<span>/</span><a data-nav="cat">${esc(cat.name)}</a>`);
  }
  if(nav.projectId){
    const proj = state.projects.find(p=>p.id===nav.projectId);
    if(proj) parts.push(`<span>/</span><a data-nav="proj">${esc(proj.name)}</a>`);
  }
  els.breadcrumb.innerHTML = parts.join(' ');
  els.breadcrumb.querySelectorAll('a').forEach(a=>{
    a.addEventListener('click', ()=>{
      const navto=a.dataset.nav;
      if(navto==='home'){ nav={categoryId:null, projectId:null}; renderAll(); }
      if(navto==='cat'){ nav.projectId=null; renderAll(); }
    });
  });
}

function renderCategories(){
  const cats = state.categories;
  els.catsGrid.innerHTML='';
  if(cats.length===0){ els.emptyCats.style.display='block'; els.catsGrid.style.display='none'; return; }
  els.emptyCats.style.display='none'; els.catsGrid.style.display='grid';
  cats.forEach(cat=>{
    const projs = state.projects.filter(p=>p.categoryId===cat.id);
    const tasks = state.tasks.filter(t=> projs.some(p=>p.id===t.projectId));
    const el=document.createElement('div');
    el.className='us-card';
    el.innerHTML=`
      <div class="us-card__top">
        <span class="us-card__badge">AREA</span>
        <span class="us-card__badge is-accent">${projs.length} PROY</span>
      </div>
      <div class="us-card__name">${esc(cat.name)}</div>
      <div class="us-card__meta">${tasks.length} tareas totales — ${new Date(cat.createdAt).toLocaleDateString()}</div>
      <div class="us-card__actions">
        <button class="us-btn us-btn--small" data-edit>EDITAR</button>
        <button class="us-btn us-btn--small" data-del>ELIMINAR</button>
      </div>
    `;
    el.addEventListener('click', (e)=>{
      if(e.target.closest('[data-edit]')||e.target.closest('[data-del]')) return;
      nav.categoryId=cat.id; nav.projectId=null; renderAll();
    });
    el.querySelector('[data-edit]').addEventListener('click', (e)=>{ e.stopPropagation(); openModal('editCategory', cat.id, cat.name); });
    el.querySelector('[data-del]').addEventListener('click', (e)=>{ e.stopPropagation(); deleteCategory(cat.id); });
    els.catsGrid.appendChild(el);
  });
}

function renderProjects(){
  const cat = state.categories.find(c=>c.id===nav.categoryId);
  if(!cat){ nav.categoryId=null; return renderAll(); }
  els.projectsTitle.textContent = `${esc(cat.name)} // PROYECTOS`;
  els.projectsHint.textContent = `Proyectos dentro de "${cat.name}". Entra a uno para ver su tablero Kanban.`;
  const projs = state.projects.filter(p=>p.categoryId===cat.id);
  els.projsGrid.innerHTML='';
  if(projs.length===0){ els.emptyProjs.style.display='block'; els.projsGrid.style.display='none'; }
  else{ els.emptyProjs.style.display='none'; els.projsGrid.style.display='grid'; }
  projs.forEach(proj=>{
    const tasks = state.tasks.filter(t=>t.projectId===proj.id);
    const todo = tasks.filter(t=>t.status==='todo').length;
    const doing = tasks.filter(t=>t.status==='doing').length;
    const done = tasks.filter(t=>t.status==='done').length;
    const total = tasks.length;
    const pctTodo = total ? Math.round(todo/total*100) : 0;
    const pctDoing = total ? Math.round(doing/total*100) : 0;
    const pctDone = total ? Math.round(done/total*100) : 0;
    // Ajuste para que sume 100 por redondeo
    let pcts = [pctTodo, pctDoing, pctDone];
    let sum = pcts.reduce((a,b)=>a+b,0);
    if(total && sum!==100){ pcts[2] += (100-sum); }
    const el=document.createElement('div');
    el.className='us-card';
    const progressHTML = total===0
      ? `<div class="us-progress__empty">SIN TAREAS — 0% / 0% / 0%</div>`
      : `<div class="us-progress" aria-label="Progreso ${pcts[0]}% sin comenzar, ${pcts[1]}% en proceso, ${pcts[2]}% terminado">
           <div class="us-progress__seg is-todo" style="width:${pcts[0]}%"></div>
           <div class="us-progress__seg is-doing" style="width:${pcts[1]}%"></div>
           <div class="us-progress__seg is-done" style="width:${pcts[2]}%"></div>
         </div>
         <div class="us-progress__labels">
           <span class="us-progress__label is-todo"><i></i> SIN <span class="us-progress__pct">${pcts[0]}%</span> (${todo})</span>
           <span class="us-progress__label is-doing"><i></i> PROC <span class="us-progress__pct">${pcts[1]}%</span> (${doing})</span>
           <span class="us-progress__label is-done"><i></i> TERM <span class="us-progress__pct">${pcts[2]}%</span> (${done})</span>
         </div>`;
    el.innerHTML=`
      <div class="us-card__top">
        <span class="us-card__badge">PROYECTO</span>
        <span class="us-card__badge is-accent">${tasks.length} TAREAS</span>
      </div>
      <div class="us-card__name">${esc(proj.name)}</div>
      <div class="us-card__meta">${todo} sin comenzar • ${doing} en proceso • ${done} terminado</div>
      ${progressHTML}
      <div class="us-card__actions">
        <button class="us-btn us-btn--small" data-edit>EDITAR</button>
        <button class="us-btn us-btn--small" data-del>ELIMINAR</button>
      </div>
    `;
    el.addEventListener('click', (e)=>{
      if(e.target.closest('[data-edit]')||e.target.closest('[data-del]')) return;
      nav.projectId=proj.id; pushRecent(proj.id); renderAll();
    });
    el.querySelector('[data-edit]').addEventListener('click', (e)=>{ e.stopPropagation(); openModal('editProject', proj.id, proj.name); });
    el.querySelector('[data-del]').addEventListener('click', (e)=>{ e.stopPropagation(); deleteProject(proj.id); });
    els.projsGrid.appendChild(el);
  });
}

function renderBoard(){
  const proj = state.projects.find(p=>p.id===nav.projectId);
  const cat = state.categories.find(c=>c.id===nav.categoryId);
  if(!proj){ nav.projectId=null; return renderAll(); }
  els.boardTitle.textContent = `${esc(cat?cat.name:'')} / ${esc(proj.name)}`;
  const tasks = state.tasks.filter(t=>t.projectId===proj.id);
  const todo = tasks.filter(t=>t.status==='todo');
  const doing = tasks.filter(t=>t.status==='doing');
  const done = tasks.filter(t=>t.status==='done');
  els.countTodo.textContent = todo.length;
  els.countDoing.textContent = doing.length;
  els.countDone.textContent = done.length;
  // stats displays
  els.boardStats.innerHTML = `
    <div class="us-display"><span class="us-display__label">SIN COMENZAR</span><span class="us-display__value">${String(todo.length).padStart(2,'0')}</span></div>
    <div class="us-display"><span class="us-display__label">EN PROCESO</span><span class="us-display__value">${String(doing.length).padStart(2,'0')}</span></div>
    <div class="us-display"><span class="us-display__label">TERMINADO</span><span class="us-display__value" style="color:#00c950">${String(done.length).padStart(2,'0')}</span></div>
  `;
  renderColumn(els.colTodo, todo);
  renderColumn(els.colDoing, doing);
  renderColumn(els.colDone, done);
  renderDisplays();
}

function renderColumn(container, tasks){
  container.innerHTML='';
  if(tasks.length===0){
    const empty=document.createElement('div');
    empty.style.cssText='font-size:10px;opacity:0.4;text-align:center;padding:18px;border:1px dashed #e2e8f0;border-radius:6px;letter-spacing:0.06em';
    empty.textContent='SIN TAREAS — ARRASTRA AQUÍ';
    container.appendChild(empty);
    return;
  }
  tasks.forEach(t=>{
    const d=document.createElement('div');
    d.className='us-task';
    d.draggable=true;
    d.dataset.id=t.id;
    d.dataset.status=t.status;
    d.innerHTML=`<div class="us-task__title">${esc(t.title)}</div><div class="us-task__meta"><span>${timeAgo(t.createdAt)}</span><button class="us-task__del" title="Eliminar">✕</button></div>`;
    d.addEventListener('dragstart', onDragStart);
    d.addEventListener('dragend', onDragEnd);
    // touch drag
    d.addEventListener('touchstart', onTouchStart, {passive:false});
    d.addEventListener('touchmove', onTouchMove, {passive:false});
    d.addEventListener('touchend', onTouchEnd);
    d.querySelector('.us-task__del').addEventListener('click', (e)=>{ e.stopPropagation(); deleteTask(t.id); });
    container.appendChild(d);
  });
}

// Confirmación de borrado (modal propio, funciona en web y en WebView Android)
let confirmCb=null;
function askConfirm(title, text, onOk){
  document.getElementById('confirmTitle').textContent = title;
  document.getElementById('confirmText').textContent = text;
  confirmCb = onOk;
  document.getElementById('confirmOverlay').classList.add('is-open');
  document.getElementById('confirmOverlay').setAttribute('aria-hidden','false');
}
function closeConfirm(){
  document.getElementById('confirmOverlay').classList.remove('is-open');
  document.getElementById('confirmOverlay').setAttribute('aria-hidden','true');
  confirmCb=null;
}

// CRUD
function openModal(mode, id=null, current=''){
  modalMode=mode; editId=id;
  els.modalError.textContent='';
  els.modalInput.value=current;
  if(mode==='category'){ els.modalTitle.textContent='NUEVA CATEGORIA'; els.modalInput.placeholder='Nombre (ej: TRABAJO)'; }
  if(mode==='editCategory'){ els.modalTitle.textContent='EDITAR CATEGORIA'; }
  if(mode==='project'){ els.modalTitle.textContent='NUEVO PROYECTO'; els.modalInput.placeholder='Nombre (ej: TESIS 2026)'; }
  if(mode==='editProject'){ els.modalTitle.textContent='EDITAR PROYECTO'; }
  els.modalOverlay.classList.add('is-open');
  els.modalOverlay.setAttribute('aria-hidden','false');
  setTimeout(()=>els.modalInput.focus(), 50);
}
function closeModal(){
  els.modalOverlay.classList.remove('is-open');
  els.modalOverlay.setAttribute('aria-hidden','true');
  modalMode=null; editId=null; els.modalError.textContent='';
}
function confirmModal(){
  const v = els.modalInput.value.trim();
  if(!v){ els.modalError.textContent='El nombre no puede estar vacío.'; return; }
  if(v.length>32){ els.modalError.textContent='Máximo 32 caracteres.'; return; }
  if(modalMode==='category'){
    if(state.categories.some(c=>c.name.toLowerCase()===v.toLowerCase())){ els.modalError.textContent='Ya existe una categoría con ese nombre.'; return; }
    state.categories.push({ id: uid(), name: v.toUpperCase(), createdAt: new Date().toISOString() });
  } else if(modalMode==='editCategory'){
    const cat=state.categories.find(c=>c.id===editId); if(cat) cat.name=v.toUpperCase();
  } else if(modalMode==='project'){
    if(!nav.categoryId) return;
    if(state.projects.some(p=>p.categoryId===nav.categoryId && p.name.toLowerCase()===v.toLowerCase())){ els.modalError.textContent='Ya existe un proyecto con ese nombre en esta categoría.'; return; }
    state.projects.push({ id: uid(), categoryId: nav.categoryId, name: v.toUpperCase(), createdAt: new Date().toISOString() });
  } else if(modalMode==='editProject'){
    const proj=state.projects.find(p=>p.id===editId); if(proj) proj.name=v.toUpperCase();
  }
  saveState(); closeModal(); renderAll();
}
function deleteCategory(id){
  askConfirm(
    '¿ELIMINAR ÁREA?',
    `Se eliminará la categoría y TODOS sus proyectos y tareas. Esta acción no se puede deshacer.`,
    ()=>{
      const projIds = state.projects.filter(p=>p.categoryId===id).map(p=>p.id);
      state.tasks = state.tasks.filter(t=>!projIds.includes(t.projectId));
      state.projects = state.projects.filter(p=>p.categoryId!==id);
      state.categories = state.categories.filter(c=>c.id!==id);
      if(nav.categoryId===id) nav={categoryId:null, projectId:null};
      saveState(); renderAll();
    }
  );
}
function deleteProject(id){
  askConfirm(
    '¿ELIMINAR PROYECTO?',
    `Se eliminará el proyecto y todas sus tareas. Esta acción no se puede deshacer.`,
    ()=>{
      state.tasks = state.tasks.filter(t=>t.projectId!==id);
      state.projects = state.projects.filter(p=>p.id!==id);
      if(nav.projectId===id) nav.projectId=null;
      // limpiar recientes
      try{
        const r=getRecentIds().filter(x=>x!==id);
        localStorage.setItem(LS_RECENT_KEY, JSON.stringify(r));
        setNativePrefs(LS_RECENT_KEY, JSON.stringify(r));
      }catch{}
      saveState(); renderAll();
    }
  );
}
function deleteTask(id){
  state.tasks = state.tasks.filter(t=>t.id!==id);
  saveState(); renderBoard(); renderDisplays();
}
function addTask(){
  const v = els.inputTask.value.trim();
  if(!v){ els.inputTask.focus(); return; }
  if(v.length>80){ alert('Máximo 80 caracteres'); return; }
  if(!nav.projectId) return;
  state.tasks.push({ id: uid(), projectId: nav.projectId, title: v, status:'todo', createdAt: new Date().toISOString() });
  els.inputTask.value='';
  saveState(); renderBoard();
}

// Drag & Drop desktop
function onDragStart(e){
  dragTaskId = e.currentTarget.dataset.id;
  e.currentTarget.classList.add('is-dragging');
  e.dataTransfer.effectAllowed='move';
  e.dataTransfer.setData('text/plain', dragTaskId);
}
function onDragEnd(e){ e.currentTarget.classList.remove('is-dragging'); dragTaskId=null; }

['colTodo','colDoing','colDone'].forEach(id=>{
  const colBody = document.getElementById(id);
  const col = colBody.closest('.us-col');
  col.addEventListener('dragover', (e)=>{ e.preventDefault(); colBody.classList.add('is-drag-over'); });
  col.addEventListener('dragleave', ()=> colBody.classList.remove('is-drag-over'));
  col.addEventListener('drop', (e)=>{
    e.preventDefault(); colBody.classList.remove('is-drag-over');
    const tid = dragTaskId || e.dataTransfer.getData('text/plain');
    if(!tid) return;
    const status = col.dataset.status;
    const task = state.tasks.find(t=>t.id===tid);
    if(task){ task.status=status; saveState(); renderBoard(); }
  });
});

// Touch drag simple
let touchTaskId=null, touchClone=null, touchOffset={x:0,y:0};
function onTouchStart(e){
  const t=e.touches[0];
  touchTaskId=e.currentTarget.dataset.id;
  const rect=e.currentTarget.getBoundingClientRect();
  touchOffset={x:t.clientX-rect.left, y:t.clientY-rect.top};
  // clone visual
  touchClone=e.currentTarget.cloneNode(true);
  touchClone.style.position='fixed'; touchClone.style.left=rect.left+'px'; touchClone.style.top=rect.top+'px';
  touchClone.style.width=rect.width+'px'; touchClone.style.opacity='0.9'; touchClone.style.pointerEvents='none';
  touchClone.style.zIndex='9999'; touchClone.style.transform='rotate(1deg)'; touchClone.style.borderColor='#d32f2f';
  document.body.appendChild(touchClone);
  e.currentTarget.style.opacity='0.3';
}
function onTouchMove(e){
  if(!touchClone) return;
  e.preventDefault();
  const t=e.touches[0];
  touchClone.style.left=(t.clientX-touchOffset.x)+'px';
  touchClone.style.top=(t.clientY-touchOffset.y)+'px';
  // highlight col under finger
  const el=document.elementFromPoint(t.clientX,t.clientY);
  document.querySelectorAll('.us-col__body').forEach(b=>b.classList.remove('is-drag-over'));
  const col=el && el.closest('.us-col');
  if(col) col.querySelector('.us-col__body').classList.add('is-drag-over');
}
function onTouchEnd(e){
  if(!touchClone) return;
  const t=e.changedTouches[0];
  const el=document.elementFromPoint(t.clientX,t.clientY);
  const col=el && el.closest('.us-col');
  if(col && touchTaskId){
    const status=col.dataset.status;
    const task=state.tasks.find(x=>x.id===touchTaskId);
    if(task){ task.status=status; saveState(); renderBoard(); }
  }
  document.querySelectorAll('.us-col__body').forEach(b=>b.classList.remove('is-drag-over'));
  document.querySelectorAll('.us-task').forEach(x=>x.style.opacity='');
  if(touchClone && touchClone.parentNode) touchClone.parentNode.removeChild(touchClone);
  touchClone=null; touchTaskId=null;
}

// Helpers
function esc(s){ const d=document.createElement('div'); d.textContent=s; return d.innerHTML; }
function timeAgo(iso){
  const diff = Date.now() - new Date(iso).getTime();
  const m=Math.floor(diff/60000); if(m<1) return 'ahora';
  if(m<60) return m+'m';
  const h=Math.floor(m/60); if(h<24) return h+'h';
  return Math.floor(h/24)+'d';
}

// Events
function attachEvents(){
  document.getElementById('btnAddCategory').addEventListener('click', ()=> openModal('category'));
  document.getElementById('btnAddProject').addEventListener('click', ()=> openModal('project'));
  document.getElementById('btnAddProjectEmpty')?.addEventListener('click', ()=> openModal('project'));
  document.getElementById('btnAddTask').addEventListener('click', addTask);
  els.inputTask.addEventListener('keydown', (e)=>{ if(e.key==='Enter') addTask(); });
  document.getElementById('modalCancel').addEventListener('click', closeModal);
  document.getElementById('modalConfirm').addEventListener('click', confirmModal);
  els.modalOverlay.addEventListener('click', (e)=>{ if(e.target===els.modalOverlay) closeModal(); });
  els.modalInput.addEventListener('keydown', (e)=>{ if(e.key==='Enter') confirmModal(); if(e.key==='Escape') closeModal(); });
  document.addEventListener('keydown', (e)=>{ if(e.key==='Escape' && els.modalOverlay.classList.contains('is-open')) closeModal(); });
  // confirm modal de borrado
  document.getElementById('confirmCancel').addEventListener('click', closeConfirm);
  document.getElementById('confirmAccept').addEventListener('click', ()=>{ const cb=confirmCb; closeConfirm(); if(cb) cb(); });
  document.getElementById('confirmOverlay').addEventListener('click', (e)=>{ if(e.target===document.getElementById('confirmOverlay')) closeConfirm(); });
  document.addEventListener('keydown', (e)=>{ if(e.key==='Escape' && document.getElementById('confirmOverlay').classList.contains('is-open')) closeConfirm(); });
  // mobile nav
  if(els.mobileNav){
    els.mobileNav.querySelectorAll('[data-mnav]').forEach(btn=>{
      btn.addEventListener('click', ()=>{
        const v=btn.dataset.mnav;
        if(v==='cats'){ nav={categoryId:null, projectId:null}; renderAll(); window.scrollTo({top:0, behavior:'smooth'}); }
        if(v==='recent'){ 
          nav={categoryId:null, projectId:null}; renderAll(); 
          setTimeout(()=>{ document.getElementById('widgetRecent')?.scrollIntoView({behavior:'smooth', block:'start'}); }, 50);
        }
      });
    });
  }
}

// Deep link desde widget home: index.html#project=ID
function handleDeepLink(){
  const m = location.hash.match(/project=([^&]+)/);
  if(m){
    const pid=m[1];
    const proj=state.projects.find(p=>p.id===pid);
    if(proj){ nav.categoryId=proj.categoryId; nav.projectId=proj.id; pushRecent(proj.id); }
    history.replaceState(null,'',location.pathname);
  }
}

// Knob spiral canvas
function drawKnob(){
  document.querySelectorAll('.us-knob__spiral').forEach(canvas=>{
    const ctx=canvas.getContext('2d'); const w=canvas.width, h=canvas.height;
    ctx.clearRect(0,0,w,h);
    ctx.strokeStyle='rgba(211,47,47,0.9)'; ctx.lineWidth=1.2;
    // simple spiral
    ctx.beginPath();
    for(let a=0;a<Math.PI*2.2;a+=0.08){
      const r=4 + a*3.2;
      const x=w/2 + Math.cos(a- Math.PI/2)*r;
      const y=h/2 + Math.sin(a- Math.PI/2)*r;
      if(a===0) ctx.moveTo(x,y); else ctx.lineTo(x,y);
    }
    ctx.stroke();
    // accent arc
    ctx.beginPath(); ctx.strokeStyle='#d32f2f'; ctx.lineWidth=2;
    ctx.arc(w/2,h/2,18, -Math.PI*0.75, Math.PI*0.15); ctx.stroke();
  });
}

// Init asíncrono: primero se restaura el tablero (localStorage → preferencias nativas),
// y recién después se pinta. Así los proyectos agregados nunca se pierden ni se pisan.
async function init(){
  state = await loadState();
  attachEvents();
  drawKnob();
  handleDeepLink();
  renderAll();
  // Si el arranque fue desde preferencias nativas, el tablero ya quedó en localStorage.
}
init();
