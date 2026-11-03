// The launcher window: calls the Rust commands in src/main.rs and shows their events.
const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;
const appWindow = window.__TAURI__.window?.getCurrentWindow?.();

const $ = (id) => document.getElementById(id);
const STAGES = {
  version: "Préparation…",
  natives: "Préparation du jeu…",
  java: "Vérification de Java…",
  rago: "Mise à jour de Rago…",
  loader: "Préparation de Java 21…",
};
const SIZES = ["1280x720", "1600x900", "1920x1080"];
const MAX_LINES = 4000;
const PRESET_NAMES = { performance: "Compétitif", balanced: "Équilibré", quality: "Visuel" };

let view = null;
let update = null;
let crashReport = null;
let page = "home";
let playing = false;
let servers = [];
const statuses = {};
const faces = {};
const lines = [];

// small helpers

function el(tag, className, text) {
  const e = document.createElement(tag);
  if (className) e.className = className;
  if (text !== undefined) e.textContent = text;
  return e;
}

function icon(name, extra = "") {
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("class", `i ${extra}`.trim());
  const use = document.createElementNS(ns, "use");
  use.setAttribute("href", `#i-${name}`);
  svg.append(use);
  return svg;
}

function gb(mb) {
  const g = mb / 1024;
  return `${Number.isInteger(g) ? g : g.toFixed(1).replace(".", ",")} Go`;
}

function players(n) {
  return `${n.toLocaleString("fr-FR")} joueur${n > 1 ? "s" : ""}`;
}

function ago(iso) {
  const t = Date.parse(iso);
  if (!t) return "";
  const days = Math.floor((Date.now() - t) / 86400000);
  if (days <= 0) return "aujourd'hui";
  if (days === 1) return "hier";
  if (days < 30) return `il y a ${days} jours`;
  const months = Math.floor(days / 30);
  return months < 12 ? `il y a ${months} mois` : `il y a ${Math.floor(months / 12)} an${months >= 24 ? "s" : ""}`;
}

let toastTimer = null;
function toast(text, error = false) {
  const t = $("toast");
  t.textContent = text;
  t.classList.toggle("error", error);
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), error ? 6000 : 3200);
}

function setState(text, kind = "") {
  $("state-text").textContent = text;
  $("state-dot").className = `state ${kind}`;
  $("state-text").className = kind === "playing" ? "playing" : "";
}

function setStatus(text, error = false) {
  $("status-text").textContent = text;
  $("status-text").classList.toggle("error", error);
  if (error) setState("Erreur");
}

function setProgress(fraction) {
  $("track").hidden = fraction === null;
  if (fraction !== null) $("fill").style.width = `${Math.round(fraction * 100)}%`;
}

// window

$("win-min").addEventListener("click", () => appWindow?.minimize());
$("win-max").addEventListener("click", () => appWindow?.toggleMaximize());
$("win-close").addEventListener("click", () => appWindow?.close());

// close an open menu when clicking elsewhere
document.addEventListener("click", (e) => {
  for (const d of document.querySelectorAll("details.drop[open]")) if (!d.contains(e.target)) d.open = false;
});

// pages

function showPage(name) {
  page = name;
  for (const p of document.querySelectorAll(".page")) p.hidden = p.id !== `page-${name}`;
  for (const t of document.querySelectorAll(".nav")) {
    if (t.dataset.page === name) t.setAttribute("aria-current", "page");
    else t.removeAttribute("aria-current");
  }
  if (name === "settings") litSection();
  if (name === "console") {
    $("console-dot").hidden = true;
    renderConsole();
  }
  if (name === "mods") renderMods();
  if (name === "home") {
    pollStats();
    pollConnection();
  }
  character.update();
}

for (const b of document.querySelectorAll("[data-page]")) b.addEventListener("click", () => showPage(b.dataset.page));
for (const b of document.querySelectorAll("[data-goto]")) {
  b.addEventListener("click", () => {
    showPage("settings");
    $(b.dataset.goto).scrollIntoView({ block: "start" });
  });
}

function litSection() {
  const scroll = $("settings-scroll");
  const top = scroll.getBoundingClientRect().top + 40;
  let current = "game";
  for (const g of document.querySelectorAll(".group")) if (g.getBoundingClientRect().top <= top) current = g.id;
  if (scroll.scrollTop + scroll.clientHeight >= scroll.scrollHeight - 2) current = "tools";
  for (const b of document.querySelectorAll(".subnav button")) b.classList.toggle("current", b.dataset.goto === current);
}
$("settings-scroll").addEventListener("scroll", litSection);

// the player in 3D: their own skin when signed in, Rago's otherwise. It only draws while the home
// page is on screen and the game is not running, so it never takes the graphics card from the game.

const character = {
  viewer: null,
  skin: null,
  init() {
    const box = $("character-box");
    if (!window.skinview3d) {
      box.hidden = true;
      return;
    }
    try {
      this.viewer = new skinview3d.SkinViewer({ canvas: $("character"), width: box.clientWidth || 290, height: box.clientHeight || 460, zoom: 0.7, fov: 40 });
      this.viewer.animation = new skinview3d.IdleAnimation();
      this.viewer.animation.speed = 0.8;
      this.viewer.controls.enableZoom = false;
      this.viewer.controls.enablePan = false;
      this.viewer.playerWrapper.rotation.y = 0.42;
      this.setSkin("rago-skin.png", false);
      new ResizeObserver(() => box.clientWidth && this.viewer.setSize(box.clientWidth, box.clientHeight)).observe(box);
      document.addEventListener("visibilitychange", () => this.update());
    } catch (e) {
      console.warn("3D view unavailable", e);
      box.hidden = true;
      this.viewer = null;
    }
  },
  setSkin(src, slim) {
    if (!this.viewer || this.skin === src) return;
    this.skin = src;
    this.viewer.loadSkin(src, { model: slim ? "slim" : "default" }).catch(() => {});
  },
  update() {
    if (this.viewer) this.viewer.renderPaused = page !== "home" || playing || document.hidden;
  },
};

// accounts

async function face(uuid) {
  if (!(uuid in faces)) faces[uuid] = await invoke("skin_face", { uuid }).catch(() => null);
  return faces[uuid];
}

function menuButton(content, onClick, className = "") {
  const b = el("button", className);
  if (typeof content === "string") b.textContent = content;
  else b.append(...content);
  b.addEventListener("click", onClick);
  return b;
}

async function renderAccounts() {
  const selected = view.accounts.find((a) => a.uuid === view.selected);
  $("accounts").hidden = !selected;
  $("sign-in").hidden = !!selected;
  $("sign-in").disabled = !view.signInReady;
  $("sign-in").title = view.signInReady ? "" : "Bientôt : Mojang doit encore autoriser l'application Rago.";
  const menu = $("account-menu");
  menu.replaceChildren();
  if (!selected) {
    character.setSkin("rago-skin.png", false);
    return;
  }
  $("account-name").textContent = selected.name;
  $("face-letter").textContent = selected.name.slice(0, 1).toUpperCase();
  for (const a of view.accounts) {
    const b = menuButton(a.name, () => run(b, async () => {
      await invoke("select_account", { uuid: a.uuid });
      $("accounts").open = false;
    }), a.uuid === selected.uuid ? "current" : "");
    menu.append(b);
  }
  menu.append(el("hr"));
  const add = menuButton("Ajouter un compte", () => signIn(add));
  add.disabled = !view.signInReady;
  menu.append(add);
  const remove = menuButton(`Retirer ${selected.name}`, () => run(remove, async () => {
    await invoke("remove_account", { uuid: selected.uuid });
    $("accounts").open = false;
    setStatus("");
  }), "danger");
  menu.append(remove);
  const src = await face(selected.uuid);
  $("face").hidden = !src;
  $("face-letter").hidden = !!src;
  if (src) $("face").src = src;
  const skin = await invoke("skin", { uuid: selected.uuid }).catch(() => null);
  if (skin) character.setSkin(skin.png, skin.slim);
}

// state

function render() {
  $("version").textContent = `v${view.version}`;
  renderAccounts();
  const signedIn = view.accounts.length > 0;
  playing = view.playing || $("play").dataset.running === "1";
  $("play").disabled = !signedIn || playing;
  if (!signedIn && !$("status-text").textContent) {
    setStatus(view.signInReady ? "Connecte-toi pour jouer." : "La connexion Microsoft arrive bientôt : Mojang doit encore autoriser l'application Rago.");
  }
  const rago = view.rago?.match(/^rago-(.+?)\+/);
  const versions = $("versions");
  const engine = view.engine === "rago" ? "Chargeur Rago · Java 21" : `Forge ${view.forge}`;
  versions.replaceChildren(el("span", "", "Minecraft 1.8.9"), el("span", "", engine));
  if (rago) versions.append(el("span", "", `Rago ${rago[1]}`));

  // performance card and page
  for (const b of document.querySelectorAll("[data-preset]")) b.classList.toggle("current", b.dataset.preset === view.preset);
  $("engine-setting").hidden = !view.loader;
  for (const b of document.querySelectorAll("[data-engine]")) b.classList.toggle("current", b.dataset.engine === view.engine);
  $("stat-fps").textContent = view.maxFps == null ? "120" : view.maxFps >= 260 ? "Illimité" : String(view.maxFps);
  $("memory").max = view.maxMemoryMb;
  $("memory").value = view.memoryMb;
  $("memory-value").textContent = gb(view.memoryMb);
  const pc = view.systemMemoryMb ? ` (ton PC a ${gb(Math.round(view.systemMemoryMb / 512) * 512)})` : "";
  $("memory-hint").textContent = `Recommandé : ${gb(view.recommendedMemoryMb)}${pc}. Plus que nécessaire rend chaque nettoyage de la mémoire plus long.`;
  $("tuned-jvm").checked = view.tunedJvm;
  $("high-priority").checked = view.highPriority;
  $("dedicated-gpu").checked = view.dedicatedGpu;
  for (const s of document.querySelectorAll("[data-windows]")) s.hidden = !view.windows;

  // mods page
  $("optifine-text").textContent = view.optifine
    ? `${view.optifine} est installé.`
    : "OptiFine ne peut pas être livré avec Rago. Télécharge-le sur son site, puis ajoute le fichier ici.";
  $("optifine-remove").hidden = !view.optifine;

  // settings
  const size = view.width && view.height ? `${view.width}x${view.height}` : "";
  const custom = size && !SIZES.includes(size);
  if ($("size-preset").value !== "custom" || !custom) $("size-preset").value = custom ? "custom" : size;
  $("custom-size").hidden = $("size-preset").value !== "custom";
  if (document.activeElement !== $("width")) $("width").value = view.width ?? "";
  if (document.activeElement !== $("height")) $("height").value = view.height ?? "";
  $("fullscreen").checked = view.fullscreen;
  $("auto-update").checked = view.autoUpdate;
  $("java-text").textContent = view.javaPath ? view.javaPath : "Java 8 de Temurin, installé et mis à jour par le launcher.";
  $("java-text").classList.toggle("path", !!view.javaPath);
  $("java-reset").hidden = !view.javaPath;
  if (document.activeElement !== $("jvm-args")) $("jvm-args").value = view.jvmArgs;
  $("on-launch").value = view.onLaunch;
  $("show-console").checked = view.showConsole;
  $("import").disabled = !view.officialFound;
  $("game-dir").textContent = view.gameDir;

  renderJoin();
}

async function refresh() {
  view = await invoke("state");
  render();
}

async function run(button, task) {
  if (button) button.disabled = true;
  try {
    await task();
  } catch (e) {
    setProgress(null);
    setStatus(String(e), true);
  } finally {
    if (button) button.disabled = false;
    await refresh();
  }
}

/** Saves one setting; on a refusal, says why and shows the saved value again. */
async function save(key, value) {
  try {
    await invoke("set_setting", { key, value });
  } catch (e) {
    toast(String(e), true);
  }
  await refresh();
}

function signIn(button) {
  return run(button, async () => {
    setStatus("Termine la connexion dans ton navigateur…");
    const account = await invoke("sign_in");
    setStatus(`Bienvenue ${account.name} !`);
    $("accounts").open = false;
  });
}

$("sign-in").addEventListener("click", () => signIn($("sign-in")));

function play(server = null) {
  if (playing || !view?.accounts.length) return;
  $("after-crash").hidden = true;
  $("play").dataset.running = "1";
  $("play-text").textContent = "Lancement";
  setState("Lancement…", "busy");
  showPage("home");
  run($("play"), async () => {
    setStatus("Connexion…");
    try {
      await invoke("play", { server });
    } finally {
      $("play").dataset.running = "";
      $("play-text").textContent = "Jouer";
    }
  });
}
$("play").addEventListener("click", () => play());

// where Play goes: the main menu or a server

function serverName(address) {
  return servers.find((s) => s.address === address)?.name ?? address;
}

function renderJoin() {
  if (!view) return;
  $("join-name").textContent = view.join ? serverName(view.join) : "Menu principal";
  $("join-detail").textContent = view.join ? `${view.join} · 1.8.9 Forge` : "1.8.9 Forge";
  const menu = $("join-menu");
  menu.replaceChildren();
  const option = (label, detail, value) => {
    const b = menuButton([el("span", "", label)], () => {
      $("join").open = false;
      save("join", value);
    }, (view.join ?? "") === value ? "current" : "");
    if (detail) b.firstChild.append(el("small", "", detail));
    menu.append(b);
  };
  option("Menu principal", "Le jeu s'ouvre sur son menu", "");
  for (const s of servers) option(`Rejoindre ${s.name}`, s.address, s.address);
}

// home: recent servers

async function serverStatus(address) {
  const cached = statuses[address];
  if (cached && Date.now() - cached.at < 60000) return cached.status;
  const status = await invoke("server_status", { address }).catch(() => null);
  statuses[address] = { at: Date.now(), status };
  return status;
}

async function renderServers() {
  servers = await invoke("servers").catch(() => []);
  renderJoin();
  const list = $("server-list");
  list.replaceChildren();
  for (const s of servers.slice(0, 2)) {
    const card = el("button", "server");
    const iconBox = el("span", "server-icon", s.name.slice(0, 1).toUpperCase());
    const body = el("span", "server-body");
    const name = el("strong", "", s.name);
    const detail = el("small", "", s.suggested ? "Bed Wars, SkyWars, Duels" : s.address);
    const state = el("span", "server-state", "Recherche…");
    body.append(name, detail, state);
    const go = el("span", "server-go");
    go.append(icon("right", "sm"));
    card.append(iconBox, body, go);
    card.title = `Jouer sur ${s.name}`;
    card.addEventListener("click", () => play(s.address));
    list.append(card);
    const setIcon = (src) => {
      const img = el("img", "server-icon");
      img.alt = "";
      img.src = src;
      iconBox.replaceWith(img);
    };
    if (s.icon) setIcon(s.icon);
    serverStatus(s.address).then((st) => {
      if (!st) {
        state.textContent = "Hors ligne";
        state.className = "server-state off";
        return;
      }
      state.textContent = `En ligne · ${st.pingMs} ms`;
      state.className = "server-state on";
      detail.textContent = players(st.online);
      if (st.favicon && !s.icon) setIcon(st.favicon);
    });
  }
  if (servers.length < 2) {
    const hint = el("div", "server hint");
    hint.append(el("p", "", "Ajoute tes serveurs dans Multijoueur en jeu : ils apparaissent ici, à un clic."));
    list.append(hint);
  }
  pollConnection(true);
}

// home: performance and connection, measured on this computer

let statsTimer = null;
async function pollStats() {
  clearTimeout(statsTimer);
  if (page !== "home" || document.hidden) return;
  const s = await invoke("stats").catch(() => null);
  if (s) {
    $("stat-cpu").textContent = s.cpu == null ? "–" : `${s.cpu} %`;
    $("stat-ram").textContent = s.ram == null ? "–" : `${s.ram} %`;
    $("stat-gpu").textContent = s.gpu ?? "–";
    $("stat-gpu").title = s.gpuFull ?? "";
  }
  statsTimer = setTimeout(pollStats, 3000);
}

let connTimer = null;
let connBusy = false;
async function pollConnection(now = false) {
  clearTimeout(connTimer);
  if (page !== "home" || document.hidden || (connBusy && !now)) return;
  const address = view?.join || servers[0]?.address || "mc.hypixel.net";
  $("conn-target").textContent = `vers ${serverName(address)}`;
  connBusy = true;
  const l = await invoke("connection", { address }).catch(() => null);
  connBusy = false;
  const badge = $("conn-badge");
  badge.hidden = false;
  badge.replaceChildren();
  if (!l) {
    $("conn-ping").textContent = "–";
    $("conn-jitter").textContent = "–";
    badge.className = "badge bad";
    badge.textContent = "Injoignable";
  } else {
    $("conn-ping").textContent = `${l.pingMs} ms`;
    $("conn-jitter").textContent = `${l.jitterMs} ms`;
    const stable = l.lost === 0 && l.jitterMs <= 10;
    badge.className = `badge ${stable ? "good" : "meh"}`;
    badge.append(stable ? "Stable" : "Instable");
    if (stable) badge.append(icon("check", "sm"));
  }
  connTimer = setTimeout(pollConnection, 30000);
}

document.addEventListener("visibilitychange", () => {
  if (!document.hidden) {
    pollStats();
    pollConnection();
  }
});

// home: news

async function renderNews() {
  const news = await invoke("news").catch(() => []);
  const n = news[0];
  $("news-item").hidden = !n;
  $("news-empty").hidden = !!n;
  $("news-all").hidden = !n;
  if (!n) return;
  $("news-date").textContent = ago(n.date);
  $("news-title").textContent = n.title;
  $("news-summary").textContent = n.summary;
  $("news-more").onclick = () => invoke("open_release", { url: n.url });
  $("news-all").onclick = () => invoke("open_release", { url: "https://github.com/kaliso-dev/Rago-Client/releases/" });
}

// notifications

function renderNotes() {
  const notes = $("notes");
  notes.replaceChildren();
  $("bell-dot").hidden = !update;
  if (!update) {
    notes.append(el("p", "empty-note", "Aucune notification."));
    return;
  }
  const note = el("div", "note");
  note.append(el("p", "", `Une nouvelle version du launcher est prête : v${update.version}.`));
  const go = el("button", "primary", "Mettre à jour");
  go.addEventListener("click", () =>
    run(go, async () => {
      go.textContent = "Téléchargement…";
      await invoke("install_update", { update });
    }));
  note.append(go);
  notes.append(note);
}

// presets and the optimizer

for (const b of document.querySelectorAll("[data-preset]")) {
  b.addEventListener("click", async () => {
    try {
      await invoke("apply_preset", { name: b.dataset.preset });
      toast(`Profil ${PRESET_NAMES[b.dataset.preset]} appliqué. Il prend effet au prochain lancement.`);
    } catch (e) {
      toast(String(e), true);
    }
    await refresh();
  });
}

$("optimize").addEventListener("click", async () => {
  try {
    await invoke("optimize");
    toast(view.windows ? "Optimisé : profil Compétitif, Java optimisé et priorité haute." : "Optimisé : profil Compétitif et Java optimisé.");
  } catch (e) {
    toast(String(e), true);
  }
  await refresh();
});

// mods

async function renderMods() {
  const mods = await invoke("mods").catch(() => []);
  const list = $("mod-list");
  list.replaceChildren();
  if (!mods.length) list.append(el("p", "muted small-text", "Aucun mod pour l'instant : Rago s'installe au premier lancement."));
  for (const m of mods) {
    const row = el("div", `mod${m.enabled ? "" : " off"}`);
    const ic = el("span", `mod-icon ${m.kind}`);
    if (m.kind === "rago") {
      const img = el("img");
      img.src = "logo.png";
      img.width = img.height = 22;
      img.alt = "";
      ic.append(img);
    } else ic.append(icon(m.kind === "optifine" ? "monitor" : "mods"));
    const body = el("span", "mod-body");
    body.append(el("strong", "", m.file), el("small", "", m.kind === "rago" ? "Le client, toujours activé" : m.kind === "optifine" ? "Graphismes" : "Mod Forge"));
    row.append(ic, body);
    if (m.kind !== "rago") {
      const sw = el("input", "switch");
      sw.type = "checkbox";
      sw.checked = m.enabled;
      sw.setAttribute("aria-label", `Activer ${m.file}`);
      sw.addEventListener("change", async () => {
        try {
          await invoke("set_mod_enabled", { file: m.enabled ? m.file : `${m.file}.disabled`, enabled: sw.checked });
        } catch (e) {
          toast(String(e), true);
        }
        renderMods();
      });
      row.append(sw);
    }
    list.append(row);
  }
}

// settings

$("memory").addEventListener("input", (e) => ($("memory-value").textContent = gb(Number(e.target.value))));
$("memory").addEventListener("change", (e) => save("memoryMb", Number(e.target.value)));
for (const [id, key] of [["tuned-jvm", "tunedJvm"], ["high-priority", "highPriority"], ["dedicated-gpu", "dedicatedGpu"], ["fullscreen", "fullscreen"], ["auto-update", "autoUpdate"], ["show-console", "showConsole"]]) {
  $(id).addEventListener("change", (e) => save(key, e.target.checked));
}
$("on-launch").addEventListener("change", (e) => save("onLaunch", e.target.value));
for (const b of document.querySelectorAll("[data-engine]")) {
  b.addEventListener("click", async () => {
    if (b.dataset.engine === view.engine) return;
    await save("engine", b.dataset.engine);
    if (view.engine === b.dataset.engine) toast(b.dataset.engine === "rago" ? "Chargeur Rago choisi : le prochain lancement prépare Java 21." : "Forge choisi pour le prochain lancement.");
  });
}

$("size-preset").addEventListener("change", (e) => {
  const v = e.target.value;
  $("custom-size").hidden = v !== "custom";
  if (v === "custom") {
    $("width").focus();
    return;
  }
  const [width, height] = v ? v.split("x").map(Number) : [null, null];
  save("size", { width, height });
});
for (const id of ["width", "height"]) {
  $(id).addEventListener("change", () => {
    const width = Number($("width").value) || null;
    const height = Number($("height").value) || null;
    if (width && height) save("size", { width, height });
  });
}

async function saveJvmArgs() {
  const value = $("jvm-args").value;
  if (value.trim() === view.jvmArgs) return;
  try {
    await invoke("set_setting", { key: "jvmArgs", value });
    $("jvm-error").hidden = true;
    toast("Arguments enregistrés.");
  } catch (e) {
    $("jvm-error").textContent = String(e);
    $("jvm-error").hidden = false;
    return;
  }
  await refresh();
}
$("jvm-args").addEventListener("keydown", (e) => e.key === "Enter" && saveJvmArgs());
$("jvm-args").addEventListener("blur", saveJvmArgs);

$("java-pick").addEventListener("click", () =>
  run($("java-pick"), async () => {
    try {
      const version = await invoke("pick_java");
      if (version) toast(`Java ${version} choisi.`);
    } catch (e) {
      toast(String(e), true);
    }
  }));
$("java-reset").addEventListener("click", () => run($("java-reset"), () => invoke("reset_java")));

// OptiFine and tools

$("optifine-page").addEventListener("click", () => invoke("open_optifine_page"));
$("optifine-add").addEventListener("click", () =>
  run($("optifine-add"), async () => {
    try {
      const name = await invoke("add_optifine");
      if (name) toast(`${name} ajouté.`);
    } catch (e) {
      toast(String(e), true);
    }
    renderMods();
  }));
$("optifine-remove").addEventListener("click", () => run($("optifine-remove"), async () => {
  await invoke("remove_optifine");
  renderMods();
}));

for (const b of document.querySelectorAll("[data-folder]")) {
  b.addEventListener("click", () => invoke("open_folder", { which: b.dataset.folder }).catch((e) => toast(String(e), true)));
}
$("open-console").addEventListener("click", () => showPage("console"));
$("open-crash").addEventListener("click", () => crashReport && invoke("open_crash_report", { path: crashReport }));

$("import").addEventListener("click", () =>
  run($("import"), async () => {
    try {
      const copied = await invoke("import_official");
      toast(copied.length ? `Importé : ${copied.length} élément${copied.length > 1 ? "s" : ""} (options, serveurs, packs).` : "Rien de nouveau à importer.");
      renderServers();
    } catch (e) {
      toast(String(e), true);
    }
  }));

$("repair").addEventListener("click", () =>
  run($("repair"), async () => {
    try {
      await invoke("repair");
      toast("Le jeu et Java seront retéléchargés au prochain lancement.");
    } catch (e) {
      toast(String(e), true);
    }
  }));

// console

function lineClass(line) {
  if (/\b(ERROR|FATAL)\b|Exception|^\s+at /.test(line)) return "error";
  if (/\bWARN\b/.test(line)) return "warn";
  return "";
}

function lineNode(line) {
  const d = el("div", lineClass(line), line);
  return d;
}

function matches(line) {
  const f = $("console-filter").value.trim().toLowerCase();
  return !f || line.toLowerCase().includes(f);
}

function renderConsole() {
  const out = $("console-out");
  const frag = document.createDocumentFragment();
  for (const l of lines) if (matches(l)) frag.append(lineNode(l));
  out.replaceChildren(frag);
  $("console-empty").hidden = lines.length > 0;
  if ($("console-follow").checked) out.scrollTop = out.scrollHeight;
}

function addLines(batch) {
  lines.push(...batch);
  const extra = lines.length - MAX_LINES;
  if (extra > 0) lines.splice(0, extra);
  if (page !== "console") {
    $("console-dot").hidden = false;
    return;
  }
  const out = $("console-out");
  for (const l of batch) if (matches(l)) out.append(lineNode(l));
  while (out.childElementCount > MAX_LINES) out.firstElementChild.remove();
  $("console-empty").hidden = true;
  if ($("console-follow").checked) out.scrollTop = out.scrollHeight;
}

$("console-filter").addEventListener("input", renderConsole);
$("console-follow").addEventListener("change", (e) => e.target.checked && ($("console-out").scrollTop = $("console-out").scrollHeight));
$("console-copy").addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(lines.filter(matches).join("\n"));
    toast("Console copiée.");
  } catch {
    toast("La copie a échoué : sélectionne le texte et fais Ctrl+C.", true);
  }
});

// events from the game

listen("progress", ({ payload }) => {
  if (payload.stage === "files") {
    setStatus(`Vérification des fichiers de Minecraft : ${payload.done} sur ${payload.total}` + (payload.megabytes ? ` (${payload.megabytes} Mo téléchargés)` : ""));
    setProgress(payload.total ? payload.done / payload.total : 0);
    setState(`Fichiers ${payload.done}/${payload.total}`, "busy");
  } else {
    setStatus(STAGES[payload.stage] ?? "…");
    setState(STAGES[payload.stage] ?? "…", "busy");
    setProgress(null);
  }
});
listen("playing", () => {
  playing = true;
  character.update();
  setProgress(null);
  setStatus("Bon jeu !");
  setState("En jeu", "playing");
  $("play-text").textContent = "En jeu";
  lines.length = 0;
  renderConsole();
  if (view?.showConsole) showPage("console");
});
listen("console", ({ payload }) => addLines(payload));
listen("closed", ({ payload }) => {
  playing = false;
  character.update();
  $("play-text").textContent = "Jouer";
  const ok = payload.code === 0 || payload.code === null;
  crashReport = payload.crashReport;
  $("after-crash").hidden = ok;
  $("open-crash").hidden = !crashReport;
  setStatus(ok ? "" : `Le jeu s'est fermé sur une erreur (code ${payload.code}).`, !ok);
  if (ok) setState("Prêt");
  refresh();
  renderServers();
});

// start

character.init();
refresh().then(async () => {
  setState(view.playing ? "En jeu" : "Prêt", view.playing ? "playing" : "");
  character.update();
  // a game still running (the window was reloaded): its output so far
  lines.push(...(await invoke("console")));
  renderConsole();
  renderServers();
  pollStats();
});
renderNews();
renderNotes();
invoke("check_update")
  .then((u) => {
    update = u;
    renderNotes();
  })
  .catch(() => {});
