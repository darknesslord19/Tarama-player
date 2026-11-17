/* DarkStream – HTML arayüz
 * Uygulama içinde: istekler ve oynatma window.Android köprüsüyle yapılır (CORS yok).
 * Tarayıcıda: fetch + <video>/hls.js ile çalışır (CORS kuralları geçerlidir).
 */
'use strict';

const isApp = typeof window.Android !== 'undefined';
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const lower = (s) => String(s || '').toLocaleLowerCase('tr');

/* ---------- Depolama ---------- */
const store = {
  get(k, d) {
    try {
      const v = isApp ? Android.load(k) : localStorage.getItem(k);
      return v ? JSON.parse(v) : d;
    } catch (e) { return d; }
  },
  set(k, v) {
    const s = JSON.stringify(v);
    try { isApp ? Android.save(k, s) : localStorage.setItem(k, s); } catch (e) { /* yok say */ }
  },
};

/* ---------- Ağ ---------- */
const pending = {};
let seq = 0;
window.__nativeResponse = (id, status, body, err) => {
  const p = pending[id];
  if (!p) return;
  delete pending[id];
  if (err || !status || status >= 400) p.reject(new Error(err || 'HTTP ' + status));
  else p.resolve(body);
};
function getText(url, headers = {}) {
  if (isApp) {
    return new Promise((resolve, reject) => {
      const id = 'r' + (++seq);
      pending[id] = { resolve, reject };
      Android.request(id, url, JSON.stringify(headers));
    });
  }
  const u = url.startsWith('asset://') ? url.slice(8) : url;
  return fetch(u, { headers }).then((r) => {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.text();
  });
}

/* ---------- Depolar (repo) ---------- */
const BUILTIN = { url: 'asset://catalog.json', builtin: true };
let repos = [];        // {url, name, categories, error}
let allItems = [];     // düz liste (arama için)
let byUid = new Map();  // uid -> içerik

function repoList() { return [BUILTIN, ...store.get('repos', [])]; }

async function loadRepos() {
  $('#main').innerHTML = '<div class="loading">Yükleniyor…</div>';
  const list = repoList();
  repos = await Promise.all(list.map(async (r) => {
    try {
      const text = await getText(r.url, r.headers || {});
      const data = text.trim().startsWith('#EXTM3U') ? parseM3U(text, r.url) : normalize(JSON.parse(text), r.url);
      return { ...r, ...data };
    } catch (e) {
      return { ...r, name: r.name || r.url, categories: [], error: e.message };
    }
  }));
  allItems = [];
  byUid = new Map();
  repos.forEach((repo) => repo.categories.forEach((cat) => cat.items.forEach((it) => {
    it.repoName = repo.name;
    allItems.push(it);
    if (!byUid.has(it.uid)) byUid.set(it.uid, it);
  })));
  repos.filter((r) => r.error).forEach((r) => toast(`${r.name}: yüklenemedi (${r.error})`));
  renderHome();
}

function normalize(data, url) {
  let cats = data.categories;
  if (!Array.isArray(cats)) cats = [{ title: data.name || 'İçerikler', items: data.items || [] }];
  cats = cats.map((c) => ({
    title: c.title || 'Diğer',
    items: (c.items || []).map((it, i) => ({
      ...it,
      type: it.type || (it.seasons ? 'series' : 'movie'),
      uid: url + '|' + (it.id || it.title || i),
    })),
  }));
  return { name: data.name || url.split('/').pop(), categories: cats };
}

/* M3U / M3U8 oynatma listesi: group-title -> kategori, #EXTVLCOPT ve url|Referer=... başlıkları desteklenir */
function parseM3U(text, url) {
  const groups = new Map();
  let cur = null;
  let vlc = {};
  for (const raw of text.split(/\r?\n/)) {
    const l = raw.trim();
    if (!l) continue;
    if (l.startsWith('#EXTINF')) {
      const attrs = {};
      l.replace(/([\w-]+)="([^"]*)"/g, (_, k, v) => { attrs[k.toLowerCase()] = v; });
      const cut = l.lastIndexOf('",');
      const title = (cut > -1 ? l.slice(cut + 2) : l.slice(l.indexOf(',') + 1)).trim();
      cur = { title: title || 'Kanal', poster: attrs['tvg-logo'], group: attrs['group-title'] || 'Diğer' };
      vlc = {};
    } else if (l.startsWith('#EXTVLCOPT:')) {
      const kv = l.slice(11);
      const i = kv.indexOf('=');
      const k = kv.slice(0, i).toLowerCase();
      const v = kv.slice(i + 1);
      if (k === 'http-referrer' || k === 'http-referer') vlc.Referer = v;
      else if (k === 'http-user-agent') vlc['User-Agent'] = v;
      else if (k === 'http-origin') vlc.Origin = v;
    } else if (!l.startsWith('#') && cur) {
      let link = l;
      const headers = { ...vlc };
      const pipe = l.indexOf('|');
      if (pipe > -1) {
        link = l.slice(0, pipe);
        l.slice(pipe + 1).split('&').forEach((p) => {
          const j = p.indexOf('=');
          if (j > 0) headers[decodeURIComponent(p.slice(0, j))] = decodeURIComponent(p.slice(j + 1));
        });
      }
      if (!groups.has(cur.group)) groups.set(cur.group, []);
      groups.get(cur.group).push({
        uid: url + '|' + link,
        title: cur.title,
        poster: cur.poster,
        type: 'live',
        sources: [{ name: 'Canlı', url: link, headers }],
      });
      cur = null;
    }
  }
  const name = decodeURIComponent(url.split('/').pop().split('?')[0] || 'M3U');
  return { name, categories: [...groups].map(([title, items]) => ({ title, items })) };
}

/* ---------- İzleme geçmişi ---------- */
function progressMap() { return store.get('progress', {}); }
function saveProgress(key, pos, dur, meta) {
  const p = progressMap();
  if (!dur || pos < 5000) return;          // canlı yayın ya da hemen kapatıldı
  p[key] = { ...(p[key] || {}), ...meta, pos, dur, t: Date.now() };
  store.set('progress', p);
}

/* ---------- Ana sayfa ---------- */
const ROW_LIMIT = 40;

function posterHtml(it) {
  const pct = it._pct ? `<div class="bar" style="width:${it._pct}%"></div>` : '';
  const badge = it.type === 'live' ? '<div class="badge">CANLI</div>' : '';
  const ph = `<div class="ph" style="background:${colorFor(it.title)}">${esc(it.title)}</div>`;
  const img = it.poster
    ? `<img loading="lazy" src="${esc(it.poster)}" alt="" onerror="this.outerHTML=this.dataset.ph" data-ph="${esc(ph)}">`
    : ph;
  return `<div class="poster">${img}${badge}${pct}</div>`;
}
function cardHtml(it) {
  const cls = 'card' + (it.type === 'live' ? ' wide live' : '');
  return `<button class="${cls}" data-u="${esc(it.uid)}">${posterHtml(it)}<div class="name">${esc(it.title)}</div></button>`;
}
function colorFor(s) {
  let h = 0;
  for (const c of String(s)) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `linear-gradient(135deg,hsl(${h},55%,32%),hsl(${(h + 40) % 360},60%,18%))`;
}

function renderHome() {
  const q = lower($('#search').value.trim());
  const main = $('#main');
  if (q) {
    const hits = allItems.filter((it) => lower(it.title).includes(q));
    main.innerHTML = hits.length
      ? `<div class="row-head"><h2>Sonuçlar <small>${hits.length}</small></h2></div><div class="grid">${hits.slice(0, 300).map((it) => cardHtml(it)).join('')}</div>`
      : '<div class="empty">Sonuç yok.</div>';
    return;
  }
  let html = '';
  const cont = continueItems();
  if (cont.length) html += rowHtml('İzlemeye devam et', cont, null);
  repos.forEach((repo, ri) => repo.categories.forEach((cat, ci) => {
    if (!cat.items.length) return;
    html += rowHtml(cat.title, cat.items, `${ri}:${ci}`, repos.length > 1 ? repo.name : '');
  }));
  main.innerHTML = html || '<div class="empty">İçerik yok. Ayarlardan bir depo ekleyin.</div>';
}

function rowHtml(title, items, catKey, sub = '') {
  const more = catKey && items.length > ROW_LIMIT
    ? `<button data-all="${catKey}">Tümü (${items.length}) ›</button>` : '';
  return `<section class="row"><div class="row-head"><h2>${esc(title)}${sub ? `<small>${esc(sub)}</small>` : ''}</h2>${more}</div>
    <div class="strip">${items.slice(0, ROW_LIMIT).map((it) => cardHtml(it)).join('')}</div></section>`;
}

function continueItems() {
  const p = progressMap();
  return Object.entries(p)
    .filter(([, v]) => v.dur && v.pos / v.dur < 0.95 && byUid.has(v.uid))
    .sort((a, b) => b[1].t - a[1].t)
    .slice(0, 20)
    .map(([, v]) => ({ ...byUid.get(v.uid), _pct: Math.round((v.pos / v.dur) * 100) }));
}

/* ---------- Katmanlar (detay, ayarlar, tümü) ---------- */
const layers = [];
function pushLayer(html, onMount) {
  const el = document.createElement('div');
  el.className = 'layer';
  el.innerHTML = `<button class="close" data-close>✕</button>${html}`;
  $('#layers').appendChild(el);
  layers.push({ el, prevFocus: document.activeElement });
  onMount && onMount(el);
  setTimeout(() => (el.querySelector('[data-autofocus]') || el.querySelector('button:not(.close)') || el.querySelector('button'))?.focus(), 30);
  return el;
}
function popLayer() {
  const l = layers.pop();
  if (!l) return false;
  l.el.remove();
  l.prevFocus && document.contains(l.prevFocus) && l.prevFocus.focus();
  return true;
}

function openAll(catKey) {
  const [ri, ci] = catKey.split(':').map(Number);
  const cat = repos[ri].categories[ci];
  const items = cat.items;
  const el = pushLayer(`<div class="layer-head">${esc(cat.title)} <small style="color:var(--muted)">${items.length}</small></div>
    <div class="grid">${items.map((it) => cardHtml(it)).join('')}</div>`);
  bindCards(el);
}

function openDetail(it) {
  if (it.type === 'live') { playItem(it, it.sources, it.uid); return; }
  const hero = it.backdrop || it.poster || '';
  const meta = [it.year, it.type === 'series' ? 'Dizi' : 'Film', it.duration, it.repoName].filter(Boolean).map(esc).join(' · ');
  let body = '';
  if (it.type === 'series') {
    body = '<div class="section-title">Sezonlar</div><div class="chips" id="seasons"></div><div id="eps" style="margin-top:12px"></div>';
  } else {
    const p = progressMap()[it.uid];
    const resume = p && p.dur && p.pos / p.dur < 0.95;
    body = `<div style="margin:16px 0 4px">
        <button class="btn primary" data-play data-autofocus>▶ ${resume ? 'Devam et' : 'Oynat'}</button>
        ${resume ? '<button class="btn" data-restart>Baştan başlat</button>' : ''}
      </div>
      ${(it.sources || []).length > 1 ? `<div class="section-title">Kaynaklar</div><div class="chips">${it.sources.map((s, i) =>
        `<button class="chip" data-src="${i}">${esc(s.name || 'Kaynak ' + (i + 1))}</button>`).join('')}</div>` : ''}`;
  }
  const el = pushLayer(`<div class="hero" style="background-image:url('${esc(hero)}')"></div>
    <div class="detail"><h1>${esc(it.title)}</h1><div class="meta">${meta}</div>
    <div class="desc">${esc(it.description || '')}</div>${body}</div>`);

  if (it.type === 'series') {
    const seasons = it.seasons || [];
    const draw = (si) => {
      $('#seasons', el).innerHTML = seasons.map((s, i) =>
        `<button class="chip ${i === si ? 'on' : ''}" data-season="${i}">${esc(s.title || 'Sezon ' + (s.season ?? i + 1))}</button>`).join('');
      const pm = progressMap();
      const eps = seasons[si]?.episodes || [];
      $('#eps', el).innerHTML = eps.map((ep, ei) => {
        const key = epKey(it, si, ei);
        const p = pm[key];
        const pct = p && p.dur ? Math.min(100, Math.round((p.pos / p.dur) * 100)) : 0;
        return `<button class="ep" data-ep="${ei}"><div class="num">${ep.episode ?? ei + 1}</div>
          <div class="t"><b>${esc(ep.title || 'Bölüm ' + (ei + 1))}</b><span>${esc(ep.description || '')}</span>
          ${pct ? `<div class="prog"><i style="width:${pct}%"></i></div>` : ''}</div></button>`;
      }).join('') || '<div class="empty">Bölüm yok.</div>';
      el.dataset.season = si;
    };
    draw(0);
    el.redraw = () => draw(Number(el.dataset.season || 0));
    el.addEventListener('click', (e) => {
      const s = e.target.closest('[data-season]');
      if (s) { draw(Number(s.dataset.season)); $('[data-season].on', el)?.focus(); return; }
      const b = e.target.closest('[data-ep]');
      if (b) {
        const si = Number(el.dataset.season || 0);
        const ep = seasons[si].episodes[Number(b.dataset.ep)];
        playItem(it, ep.sources || [], epKey(it, si, Number(b.dataset.ep)),
          `${it.title} · S${seasons[si].season ?? si + 1} B${ep.episode ?? Number(b.dataset.ep) + 1}${ep.title ? ' – ' + ep.title : ''}`);
      }
    });
  } else {
    el.addEventListener('click', (e) => {
      if (e.target.closest('[data-play]')) playItem(it, it.sources || [], it.uid);
      else if (e.target.closest('[data-restart]')) playItem(it, it.sources || [], it.uid, null, true);
      else {
        const c = e.target.closest('[data-src]');
        if (c) {
          const i = Number(c.dataset.src);
          const srcs = [it.sources[i], ...it.sources.filter((_, j) => j !== i)];
          playItem(it, srcs, it.uid);
        }
      }
    });
  }
}
const epKey = (it, si, ei) => `${it.uid}#${si}:${ei}`;

/* ---------- Oynatma ---------- */
function playItem(it, sources, key, title, restart) {
  if (!sources.length) { toast('Bu içerik için kaynak yok.'); return; }
  const p = progressMap()[key];
  const start = !restart && p && p.dur && p.pos / p.dur < 0.95 ? p.pos : 0;
  current = { key, uid: it.uid, title: title || it.title };
  const payload = { key, title: current.title, start, sources };
  if (isApp) Android.play(JSON.stringify(payload));
  else webPlay(payload);
}
let current = null;

window.__onPlayerClosed = (key, pos, dur) => {
  if (current && current.key === key) saveProgress(key, pos, dur, { uid: current.uid });
  refreshAfterPlay();
};
function refreshAfterPlay() {
  const top = layers[layers.length - 1];
  if (top && top.el.redraw) top.el.redraw();
  const f = document.activeElement;
  const u = f && f.dataset && f.dataset.u;
  renderHome();
  if (layers.length) { if (f && document.contains(f)) f.focus(); return; }
  if (u) [...document.querySelectorAll('#main .card')].find((c) => c.dataset.u === u)?.focus();
}

/* Tarayıcı modu oynatıcısı */
let hls = null;
async function webPlay(payload) {
  const wrap = $('#webPlayer');
  const v = $('#video');
  $('#playerTitle').textContent = payload.title;
  wrap.classList.remove('hidden');
  let i = 0;
  const tryOne = async () => {
    const s = payload.sources[i];
    if (hls) { hls.destroy(); hls = null; }
    v.querySelectorAll('track').forEach((t) => t.remove());
    (s.subtitles || []).forEach((sub, j) => {
      const t = document.createElement('track');
      Object.assign(t, { kind: 'subtitles', src: sub.url, srclang: sub.lang || 'tr', label: sub.label || sub.lang || 'Altyazı', default: j === 0 });
      v.appendChild(t);
    });
    const isHls = s.type === 'hls' || /\.m3u8/i.test(s.url);
    if (isHls && !v.canPlayType('application/vnd.apple.mpegurl')) {
      await loadHlsJs();
      hls = new window.Hls();
      hls.on(window.Hls.Events.ERROR, (_, d) => { if (d.fatal) next(); });
      hls.loadSource(s.url);
      hls.attachMedia(v);
    } else {
      v.src = s.url;
    }
    v.onloadedmetadata = () => { if (payload.start) v.currentTime = payload.start / 1000; payload.start = 0; };
    v.play().catch(() => {});
  };
  const next = () => {
    if (++i < payload.sources.length) { toast('Kaynak açılmadı, sıradaki deneniyor…'); tryOne(); }
    else toast('Oynatılamadı (tarayıcıda CORS engeli olabilir; uygulamada deneyin).');
  };
  v.onerror = next;
  v.onended = closeWebPlayer;
  await tryOne();
  $('#closePlayer').focus();
}
function closeWebPlayer() {
  const v = $('#video');
  const wrap = $('#webPlayer');
  if (wrap.classList.contains('hidden')) return false;
  if (current) window.__onPlayerClosed(current.key, Math.floor(v.currentTime * 1000), Math.floor((v.duration || 0) * 1000) || 0);
  v.pause();
  if (hls) { hls.destroy(); hls = null; }
  v.removeAttribute('src');
  v.load();
  wrap.classList.add('hidden');
  return true;
}
function loadHlsJs() {
  if (window.Hls) return Promise.resolve();
  return new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/hls.js@1/dist/hls.min.js';
    s.onload = res;
    s.onerror = () => rej(new Error('hls.js yüklenemedi'));
    document.head.appendChild(s);
  });
}

/* ---------- Ayarlar ---------- */
function openSettings() {
  const el = pushLayer(`<div class="layer-head">Ayarlar</div><div class="set">
    <div class="section-title">Depolar</div><div id="repoList"></div>
    <div class="add"><input id="repoUrl" type="url" placeholder="https://…/catalog.json veya .m3u" autocomplete="off">
    <button class="btn primary" id="addRepo">Ekle</button></div>
    <p class="note">JSON katalog ya da M3U oynatma listesi adresi ekleyebilirsin (ör. GitHub'daki dosyanın <code>raw</code> bağlantısı).
    Uygulama içinde istekler yerel yapıldığı için CORS sorunu olmaz.</p>
    <div class="section-title">Diğer</div>
    <button class="btn" id="reload">↻ Depoları yenile</button>
    <button class="btn" id="clearHist">Geçmişi temizle</button>
    <p class="note">Mod: ${isApp ? 'Uygulama (yerel oynatıcı)' : 'Tarayıcı'}</p></div>`);
  const draw = () => {
    $('#repoList', el).innerHTML = repos.map((r, i) => `<div class="repo"><div class="t"><b>${esc(r.name)}</b>
      <span>${r.builtin ? 'Dahili örnek katalog' : esc(r.url)}</span>
      ${r.error ? `<span class="err">Hata: ${esc(r.error)}</span>` : `<span>${r.categories.reduce((n, c) => n + c.items.length, 0)} içerik</span>`}</div>
      ${r.builtin ? '' : `<button class="btn" data-del="${i}">Sil</button>`}</div>`).join('');
  };
  draw();
  el.addEventListener('click', async (e) => {
    const d = e.target.closest('[data-del]');
    if (d) {
      const url = repos[Number(d.dataset.del)].url;
      store.set('repos', store.get('repos', []).filter((r) => r.url !== url));
      await loadRepos(); draw();
    } else if (e.target.closest('#addRepo')) {
      const url = $('#repoUrl', el).value.trim();
      if (!/^https?:\/\//i.test(url)) { toast('Geçerli bir http(s) adresi girin.'); return; }
      const list = store.get('repos', []);
      if (!list.some((r) => r.url === url)) list.push({ url });
      store.set('repos', list);
      $('#repoUrl', el).value = '';
      toast('Depo ekleniyor…');
      await loadRepos(); draw();
    } else if (e.target.closest('#reload')) {
      await loadRepos(); draw(); toast('Yenilendi');
    } else if (e.target.closest('#clearHist')) {
      store.set('progress', {}); renderHome(); toast('Geçmiş temizlendi');
    }
  });
}

/* ---------- Olaylar ---------- */
function bindCards(root) {
  root.addEventListener('click', (e) => {
    const all = e.target.closest('[data-all]');
    if (all) { openAll(all.dataset.all); return; }
    const c = e.target.closest('.card[data-u]');
    const it = c && byUid.get(c.dataset.u);
    if (it) openDetail(it);
  });
}
bindCards($('#main'));
$('#layers').addEventListener('click', (e) => { if (e.target.closest('[data-close]')) popLayer(); });
$('#settingsBtn').onclick = openSettings;
$('#homeBtn').onclick = () => { while (popLayer()); $('#search').value = ''; renderHome(); window.scrollTo(0, 0); };
$('#closePlayer').onclick = closeWebPlayer;
let st;
$('#search').addEventListener('input', () => { clearTimeout(st); st = setTimeout(renderHome, 200); });

window.__onBack = () => {
  if (closeWebPlayer()) return true;
  if (popLayer()) return true;
  if ($('#search').value) { $('#search').value = ''; renderHome(); return true; }
  return false;
};

/* ---------- TV kumandası: ok tuşlarıyla yön bazlı odak ---------- */
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' || e.key === 'GoBack' || e.key === 'BrowserBack') { if (!isApp) window.__onBack(); return; }
  const dirs = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
  const d = dirs[e.key];
  if (!d) return;
  const a = document.activeElement;
  if (a && a.tagName === 'INPUT' && d[0] !== 0) return;     // imleç hareketi
  if (!$('#webPlayer').classList.contains('hidden')) return;
  const scope = layers.length ? layers[layers.length - 1].el : document.body;
  const els = [...scope.querySelectorAll('button, input, [tabindex="0"]')].filter((el) => {
    if (el === a || (!layers.length && el.closest('#layers, #webPlayer'))) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  });
  if (!a || a === document.body) { els[0]?.focus(); e.preventDefault(); return; }
  const ar = a.getBoundingClientRect();
  const ax = ar.left + ar.width / 2, ay = ar.top + ar.height / 2;
  let best = null, bestScore = Infinity;
  for (const el of els) {
    const r = el.getBoundingClientRect();
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    const dx = x - ax, dy = y - ay;
    const along = dx * d[0] + dy * d[1];
    if (along <= 2) continue;
    const across = Math.abs(d[0] ? dy : dx);
    const score = along + across * 2.5;
    if (score < bestScore) { bestScore = score; best = el; }
  }
  if (best) {
    e.preventDefault();
    best.focus();
    best.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
  }
});

/* ---------- Bildirim ---------- */
let tt;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(tt);
  tt = setTimeout(() => t.classList.add('hidden'), 3200);
}

loadRepos();
