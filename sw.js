const CACHE='idoban-pwa-v1';
const ASSETS=['./','./index.html','./styles.css','./app.js','./manifest.json','./widget.html'];
self.addEventListener('install', e=>{
  e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate', e=>{
  e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));
});
self.addEventListener('fetch', e=>{
  e.respondWith(caches.match(e.request).then(r=> r || fetch(e.request).then(res=>{
    // cache runtime GET
    if(e.request.method==='GET' && res.ok){
      const clone=res.clone();
      caches.open(CACHE).then(c=>c.put(e.request, clone));
    }
    return res;
  }).catch(()=> caches.match('./index.html'))));
});
