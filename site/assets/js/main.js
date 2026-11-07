// Rago Client — site vitrine.

// Lien de téléchargement du .jar : à remplacer par l'URL définitive (release GitHub, CDN, page compte...).
// Tant qu'il est vide, les boutons « Télécharger » mènent à la section Installer.
const DOWNLOAD_URL = '';

(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (DOWNLOAD_URL) {
    $$('[data-dl]').forEach(a => { a.href = DOWNLOAD_URL; a.removeAttribute('target'); });
  }

  // Barre de navigation : fond en verre après le haut de page
  const nav = $('#nav');
  const onScroll = () => nav.classList.toggle('scrolled', scrollY > 20);
  onScroll();
  addEventListener('scroll', onScroll, { passive: true });

  // Menu mobile
  const burger = $('#burger'), menu = $('#mobile-menu');
  burger.addEventListener('click', () => {
    const open = menu.classList.toggle('open');
    burger.setAttribute('aria-expanded', open);
  });
  $$('a', menu).forEach(a => a.addEventListener('click', () => { menu.classList.remove('open'); burger.setAttribute('aria-expanded', false); }));

  // Apparition au défilement, compteurs et barres
  const counted = new WeakSet();
  const count = el => {
    if (counted.has(el)) return; counted.add(el);
    const to = +el.dataset.count, t0 = performance.now(), d = reduce ? 0 : 1400;
    const tick = t => {
      const p = d ? Math.min(1, (t - t0) / d) : 1;
      el.textContent = Math.round(to * (1 - Math.pow(1 - p, 4)));
      if (p < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  };
  const io = new IntersectionObserver(entries => {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      e.target.classList.add('in');
      $$('[data-count]', e.target).forEach(count);
      $$('.fill', e.target).forEach(f => f.style.width = f.dataset.w + '%');
      io.unobserve(e.target);
    }
  }, { threshold: .15, rootMargin: '0px 0px -40px 0px' });
  $$('.rv, .stats, #bench').forEach(el => io.observe(el));

  // Lueur qui suit la souris sur les cartes
  $$('.card').forEach(c => c.addEventListener('pointermove', e => {
    const r = c.getBoundingClientRect();
    c.style.setProperty('--mx', (e.clientX - r.left) + 'px');
    c.style.setProperty('--my', (e.clientY - r.top) + 'px');
  }));

  // Inclinaison de la fenêtre du hero
  const tilt = $('#tilt');
  if (tilt && !reduce && matchMedia('(pointer:fine) and (min-width:1101px)').matches) {
    const hero = $('.hero');
    hero.addEventListener('pointermove', e => {
      const x = e.clientX / innerWidth - .5, y = e.clientY / innerHeight - .5;
      tilt.style.transform = `rotateY(${-9 + x * 10}deg) rotateX(${4 - y * 8}deg)`;
    });
    hero.addEventListener('pointerleave', () => tilt.style.transform = '');
  }

  // Menu Rago : onglets qui tournent tout seuls
  const show = $('#menu-showcase');
  if (show) {
    const tabs = $$('.tab', show);
    let i = 0, timer;
    const select = n => {
      i = n;
      tabs.forEach((t, k) => {
        t.setAttribute('aria-selected', k === n);
        $('#' + t.dataset.target).classList.toggle('on', k === n);
        const p = $('.prog', t); p.style.animation = 'none'; p.offsetWidth; p.style.animation = '';
      });
    };
    const loop = () => { clearInterval(timer); if (!reduce) timer = setInterval(() => select((i + 1) % tabs.length), 7000); };
    tabs.forEach((t, k) => t.addEventListener('click', () => { select(k); loop(); }));
    show.addEventListener('pointerenter', () => { clearInterval(timer); show.classList.add('paused'); });
    show.addEventListener('pointerleave', () => { show.classList.remove('paused'); loop(); });
    loop();
  }

  // Launcher : onglets
  const captions = [
    'Ton skin en 3D, le bouton JOUER, et la charge de ton PC en direct.',
    'Lance directement le menu principal ou un serveur de ta liste.',
    'Profils Compétitif, Équilibré ou Visuel, mémoire allouée et priorité du processus.',
    'Tes mods Forge et OptiFine, activés ou coupés d\'un clic.',
    'Taille de la fenêtre, Java, arguments, comportement après le lancement.',
    'Minecraft, Forge, Java 8 et Rago téléchargés et vérifiés fichier par fichier.'
  ];
  const ltabs = $$('.ltab'), limgs = $$('#launcher-stage img'), lcap = $('#launcher-caption');
  ltabs.forEach(t => t.addEventListener('click', () => {
    const n = +t.dataset.i;
    ltabs.forEach((b, k) => b.setAttribute('aria-selected', k === n));
    limgs.forEach((im, k) => im.classList.toggle('on', k === n));
    lcap.textContent = captions[n];
  }));

  // Fonds animés : image fixe, vidéo au survol (ou au toucher)
  $$('.bg-tile').forEach((tile, k) => {
    const id = tile.dataset.v;
    const img = new Image();
    img.src = `assets/video/${id}.webp`; img.alt = ''; img.loading = 'lazy'; img.width = 960; img.height = 540;
    tile.prepend(img);
    let v;
    const play = () => {
      if (reduce) return;
      if (!v) {
        v = document.createElement('video');
        Object.assign(v, { muted: true, loop: true, playsInline: true, preload: 'auto', src: `assets/video/${id}.mp4` });
        v.setAttribute('muted', '');
        tile.insertBefore(v, img.nextSibling);
      }
      v.style.opacity = 1; v.play().catch(() => {});
    };
    const stop = () => { if (v) { v.pause(); } };
    tile.addEventListener('pointerenter', e => { if (e.pointerType === 'mouse') play(); });
    tile.addEventListener('pointerleave', e => { if (e.pointerType === 'mouse') stop(); });
    tile.addEventListener('click', () => (v && !v.paused) ? stop() : play());
  });

  // Vidéo de la section finale : chargée quand elle approche
  $$('[data-lazy-video]').forEach(v => {
    if (reduce) return;
    new IntersectionObserver((es, o) => {
      if (!es[0].isIntersecting) return;
      $$('source', v).forEach(s => s.src = s.dataset.src);
      v.load(); v.play().catch(() => {});
      o.disconnect();
    }, { rootMargin: '300px' }).observe(v);
  });

  // Vidéo du hero : pas d'autoplay si l'utilisateur préfère moins d'animations
  if (reduce) $$('.hero-bg video').forEach(v => { v.removeAttribute('autoplay'); v.pause(); });
})();
