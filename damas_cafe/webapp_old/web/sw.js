// Service worker — cache da aplicação. Funciona em https ou localhost;
// em http puro (IP do café) o browser não regista SW, mas o resto funciona.
const VERSION = 'damas-cafe-v4';
const ASSETS = [
  'index.html',
  'css/style.css',
  'js/app.js',
  'js/engine.js',
  'js/net.js',
  'js/audio.js',
  'js/fx.js',
  'vendor/baloo2.woff2',
  'vendor/luckiest-guy.woff2',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/maskable-192.png',
  'icons/maskable-512.png',
  'icons/apple-touch-icon.png',
  'manifest.webmanifest',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(VERSION).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;
  // as mesas (API) têm de vir sempre da rede
  if (url.pathname.startsWith('/api/')) return;

  const isPage = e.request.mode === 'navigate' || url.pathname.endsWith('.html') || url.pathname === '/';
  e.respondWith(
    caches.open(VERSION).then(async (cache) => {
      if (isPage) {
        // rede primeiro: o HTML nunca fica preso a uma versão antiga
        try {
          const res = await fetch(e.request);
          if (res.ok) cache.put(url.pathname, res.clone());
          return res;
        } catch { return cache.match(url.pathname) || cache.match('/index.html'); }
      }
      const key = url.pathname;
      const cached = await cache.match(key);
      const fresh = fetch(e.request)
        .then((res) => { if (res.ok) cache.put(key, res.clone()); return res; })
        .catch(() => cached);
      return cached || fresh;
    })
  );
});
