// The launcher window: calls the Rust commands in src/main.rs and shows their events.
const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;
const appWindow = window.__TAURI__.window?.getCurrentWindow?.();

const $ = (id) => document.getElementById(id);
const STAGES = {
  version: "Preparing…",
  natives: "Preparing the game…",
  java: "Checking Java…",
  rago: "Updating Rago…",
  loader: "Preparing Java 21…",
};
const SIZES = ["1280x720", "1600x900", "1920x1080"];
const MAX_LINES = 4000;
const PRESET_NAMES = { performance: "Competitive", balanced: "Balanced", quality: "Visual" };

let view = null;
let update = null;
let crashReport = null;
let page = "home";
let playing = false;
let servers = [];
const statuses = {};
const faces = {};
const skins = {};
const calm = window.matchMedia("(prefers-reduced-motion: reduce)");
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
  return `${Number.isInteger(g) ? g : g.toFixed(1)} GB`;
}

function players(n) {
  return `${n.toLocaleString("en-US")} player${n === 1 ? "" : "s"}`;
}

function ago(iso) {
  const t = Date.parse(iso);
  if (!t) return "";
  const days = Math.floor((Date.now() - t) / 86400000);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 30) return `${days} days ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return months === 1 ? "1 month ago" : `${months} months ago`;
  const years = Math.floor(months / 12);
  return years === 1 ? "1 year ago" : `${years} years ago`;
}

/** A short fade and lift on an element whose content just changed; nothing when motion is reduced. */
function flash(node) {
  if (calm.matches || document.hidden || !node.animate) return;
  node.animate([{ opacity: 0.2, transform: "translateY(3px)" }, { opacity: 1, transform: "none" }], { duration: 260, easing: "cubic-bezier(0.22, 1, 0.36, 1)" });
}

/** Sets a text only when it changed, so nothing repaints or animates for the same value. */
function setText(node, text, animate = true) {
  if (node.textContent === text) return;
  node.textContent = text;
  if (animate) flash(node);
}

let toastTimer = null;
function toast(text, error = false) {
  const t = $("toast");
  t.textContent = text;
  t.classList.toggle("error", error);
  if (t.hidden) {
    t.hidden = false;
    void t.offsetWidth; // once, so the first toast slides in rather than appearing
  }
  t.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove("show"), error ? 6000 : 3200);
}

function setState(text, kind = "") {
  $("state-text").textContent = text;
  $("state-dot").className = `state ${kind}`;
  $("state-text").className = kind === "playing" ? "playing" : "";
}

function setStatus(text, error = false, animate = true) {
  setText($("status-text"), text, animate);
  $("status-text").classList.toggle("error", error);
  if (error) setState("Error", "error");
}

/** null hides the bar, "busy" shows a bar that moves without a known end, a number fills it. */
function setProgress(fraction) {
  const track = $("track");
  track.hidden = fraction === null;
  track.classList.toggle("busy", fraction === "busy");
  if (typeof fraction === "number") $("fill").style.transform = `scaleX(${Math.min(1, Math.max(0, fraction)).toFixed(3)})`;
}

/** Whether the game runs: the launcher then stops its looping animations and its 3D view. */
function setPlaying(on) {
  playing = on;
  document.body.classList.toggle("playing", on);
  character.update();
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
  if (name === "friends") renderFriends();
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
// once per frame at most, however fast the wheel turns
let litQueued = false;
$("settings-scroll").addEventListener("scroll", () => {
  if (litQueued) return;
  litQueued = true;
  requestAnimationFrame(() => {
    litQueued = false;
    litSection();
  });
}, { passive: true });

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
    this.viewer.loadSkin(src, { model: slim ? "slim" : "default" }).then(() => flash($("character"))).catch(() => {});
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
  $("sign-in").title = view.signInReady ? "" : "Coming soon: Mojang still has to approve the Rago app.";
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
      renderPoints();
      if (page === "friends") renderFriends();
    }), a.uuid === selected.uuid ? "current" : "");
    menu.append(b);
  }
  menu.append(el("hr"));
  const add = menuButton("Add account", () => signIn(add));
  add.disabled = !view.signInReady;
  menu.append(add);
  const remove = menuButton(`Remove ${selected.name}`, () => run(remove, async () => {
    await invoke("remove_account", { uuid: selected.uuid });
    $("accounts").open = false;
    setStatus("");
  }), "danger");
  menu.append(remove);
  const src = await face(selected.uuid);
  $("face").hidden = !src;
  $("face-letter").hidden = !!src;
  if (src) $("face").src = src;
  // asked once per account: render() runs after every action and must not fetch the skin each time
  if (!(selected.uuid in skins)) skins[selected.uuid] = await invoke("skin", { uuid: selected.uuid }).catch(() => null);
  const skin = skins[selected.uuid];
  if (skin) character.setSkin(skin.png, skin.slim);
}

// state

function render() {
  $("version").textContent = `v${view.version}`;
  renderAccounts();
  const signedIn = view.accounts.length > 0;
  playing = view.playing || $("play").dataset.running === "1";
  document.body.classList.toggle("playing", !!view.playing);
  character.update();
  $("play").disabled = !signedIn || playing;
  if (!signedIn && !$("status-text").textContent) {
    setStatus(view.signInReady ? "Sign in to play." : "Microsoft sign-in is coming soon: Mojang still has to approve the Rago app.");
  }
  const rago = view.rago?.match(/^rago-(.+?)\+/);
  const versions = $("versions");
  const engine = view.engine === "rago" ? "Rago loader · Java 21" : `Forge ${view.forge}`;
  versions.replaceChildren(el("span", "", "Minecraft 1.8.9"), el("span", "", engine));
  if (rago) versions.append(el("span", "", `Rago ${rago[1]}`));

  // performance card and page
  for (const b of document.querySelectorAll("[data-preset]")) b.classList.toggle("current", b.dataset.preset === view.preset);
  $("engine-setting").hidden = !view.loader;
  for (const b of document.querySelectorAll("[data-engine]")) b.classList.toggle("current", b.dataset.engine === view.engine);
  $("stat-fps").textContent = view.maxFps == null ? "120" : view.maxFps >= 260 ? "Unlimited" : String(view.maxFps);
  $("memory").max = view.maxMemoryMb;
  $("memory").value = view.memoryMb;
  $("memory-value").textContent = gb(view.memoryMb);
  const pc = view.systemMemoryMb ? ` (your PC has ${gb(Math.round(view.systemMemoryMb / 512) * 512)})` : "";
  $("memory-hint").textContent = `Recommended: ${gb(view.recommendedMemoryMb)}${pc}. More than needed makes every memory cleanup take longer.`;
  $("tuned-jvm").checked = view.tunedJvm;
  $("high-priority").checked = view.highPriority;
  $("dedicated-gpu").checked = view.dedicatedGpu;
  for (const s of document.querySelectorAll("[data-windows]")) s.hidden = !view.windows;

  // mods page
  $("optifine-text").textContent = view.optifine
    ? `${view.optifine} is installed.`
    : "OptiFine can't ship with Rago. Download it from its website, then add the file here.";
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
  $("java-text").textContent = view.javaPath ? view.javaPath : view.engine === "rago" ? "Temurin Java 21, installed and kept up to date by the launcher." : "Temurin Java 8, installed and kept up to date by the launcher.";
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
    setStatus("Finish signing in in your browser…");
    const account = await invoke("sign_in");
    setStatus(`Welcome, ${account.name}!`);
    renderPoints();
    $("accounts").open = false;
  });
}

$("sign-in").addEventListener("click", () => signIn($("sign-in")));

function play(server = null) {
  if (playing || !view?.accounts.length) return;
  $("after-crash").hidden = true;
  $("play").dataset.running = "1";
  $("play-text").textContent = "Launching";
  setState("Launching…", "busy");
  showPage("home");
  run($("play"), async () => {
    setStatus("Signing in…");
    setProgress("busy");
    try {
      await invoke("play", { server });
    } finally {
      $("play").dataset.running = "";
      $("play-text").textContent = "Play";
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
  $("join-name").textContent = view.join ? serverName(view.join) : "Main menu";
  const engine = view.engine === "rago" ? "1.8.9 Rago" : "1.8.9 Forge";
  $("join-detail").textContent = view.join ? `${view.join} · ${engine}` : engine;
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
  option("Main menu", "The game opens on its menu", "");
  for (const s of servers) option(`Join ${s.name}`, s.address, s.address);
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
    const state = el("span", "server-state", "Pinging…");
    body.append(name, detail, state);
    const go = el("span", "server-go");
    go.append(icon("right", "sm"));
    card.append(iconBox, body, go);
    card.title = `Play on ${s.name}`;
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
        state.textContent = "Offline";
        state.className = "server-state off";
        return;
      }
      state.textContent = `Online · ${st.pingMs} ms`;
      state.className = "server-state on";
      detail.textContent = players(st.online);
      if (st.favicon && !s.icon) setIcon(st.favicon);
    });
  }
  if (servers.length < 2) {
    const hint = el("div", "server hint");
    hint.append(el("p", "", "Add your servers in Multiplayer in game: they show up here, one click away."));
    list.append(hint);
  }
  pollConnection(true);
}

// home: performance and connection, measured on this computer

let statsTimer = null;
async function pollStats() {
  clearTimeout(statsTimer);
  // nothing measured while the game runs: the game gets the processor and the network
  if (page !== "home" || document.hidden || playing) return;
  const s = await invoke("stats").catch(() => null);
  if (s) {
    setText($("stat-cpu"), s.cpu == null ? "–" : `${s.cpu}%`);
    setText($("stat-ram"), s.ram == null ? "–" : `${s.ram}%`);
    setText($("stat-gpu"), s.gpu ?? "–");
    $("stat-gpu").title = s.gpuFull ?? "";
  }
  statsTimer = setTimeout(pollStats, 3000);
}

let connTimer = null;
let connBusy = false;
async function pollConnection(now = false) {
  clearTimeout(connTimer);
  if (page !== "home" || document.hidden || playing || (connBusy && !now)) return;
  const address = view?.join || servers[0]?.address || "mc.hypixel.net";
  $("conn-target").textContent = `to ${serverName(address)}`;
  connBusy = true;
  const l = await invoke("connection", { address }).catch(() => null);
  connBusy = false;
  const badge = $("conn-badge");
  badge.hidden = false;
  badge.replaceChildren();
  if (!l) {
    setText($("conn-ping"), "–");
    setText($("conn-jitter"), "–");
    badge.className = "badge bad";
    badge.textContent = "Unreachable";
  } else {
    setText($("conn-ping"), `${l.pingMs} ms`);
    setText($("conn-jitter"), `${l.jitterMs} ms`);
    const stable = l.lost === 0 && l.jitterMs <= 10;
    badge.className = `badge ${stable ? "good" : "meh"}`;
    badge.append(stable ? "Stable" : "Unstable");
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

// the home card shows online friends or news, one at a time
let feed = "friends";
let newsReady = false;
function showFeed(which) {
  feed = which;
  for (const b of document.querySelectorAll("[data-feed]")) b.setAttribute("aria-selected", String(b.dataset.feed === which));
  $("feed-friends").hidden = which !== "friends";
  $("feed-news").hidden = which !== "news";
  $("friends-all").hidden = which !== "friends";
  $("news-all").hidden = which !== "news" || !newsReady;
  if (which === "friends") fitOnline();
}
for (const b of document.querySelectorAll("[data-feed]")) b.addEventListener("click", () => showFeed(b.dataset.feed));

async function renderNews() {
  const news = await invoke("news").catch(() => []);
  const n = news[0];
  $("news-item").hidden = !n;
  $("news-empty").hidden = !!n;
  newsReady = !!n;
  $("news-all").hidden = !n || feed !== "news";
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
    notes.append(el("p", "empty-note", "No notifications."));
    return;
  }
  const note = el("div", "note");
  note.append(el("p", "", `Launcher v${update.version} is ready. The launcher restarts to install it.`));
  const go = el("button", "primary", "Update and restart");
  go.addEventListener("click", () => installUpdate(go));
  note.append(go);
  notes.append(note);
}

// launcher updates: checked on start and every few hours; with "Keep Rago updated" on, a new
// version installs by itself when the launcher opens, the way the game's own client does it

const UPDATE_EVERY = 6 * 60 * 60 * 1000;
const TRIED_KEY = "rago.updateTried";

async function installUpdate(button) {
  if (!update) return;
  if (playing) {
    toast("Close the game first: the launcher restarts to update.", true);
    return;
  }
  if (button) button.disabled = true;
  $("play").disabled = true;
  setProgress("busy");
  setStatus(`Downloading launcher v${update.version}…`);
  try {
    // the installer closes this window and opens the new launcher when it is done
    await invoke("install_update", { update });
  } catch (e) {
    setProgress(null);
    setStatus(String(e), true);
    if (button) button.disabled = false;
    $("play").disabled = false;
  }
}

/** Whether this version was already installed by itself once (the installer may have failed). */
function triedBefore(version) {
  try {
    if (localStorage.getItem(TRIED_KEY) === version) return true;
    localStorage.setItem(TRIED_KEY, version);
  } catch {
    // no storage: still update, a failed install only asks again on the next start
  }
  return false;
}

async function checkUpdate(onStart) {
  if (playing) return;
  try {
    update = await invoke("check_update");
  } catch {
    return; // offline or GitHub busy: the next check tries again
  }
  renderNotes();
  if (update && onStart && view?.autoUpdate && !view.playing && !triedBefore(update.version)) {
    toast(`Updating the launcher to v${update.version}…`);
    installUpdate(null);
  }
}

// Rago Network: friends and points. They need the account signed in to Rago Network, which needs
// the Microsoft sign-in; without an account the friends page says so and the points stay hidden.

function networkError(e) {
  const text = String(e);
  if (/404 not_found/.test(text)) return "Friends are not on Rago Network yet: they arrive with its next update.";
  if (/Rago Network unreachable/.test(text)) return "Rago Network does not answer right now. Try again in a minute.";
  return text;
}

function friendRow(person, detail, online, actions) {
  const row = el("div", "friend");
  const head = el("span", "head");
  const letter = el("span", "face letter", person.name.slice(0, 1).toUpperCase());
  head.append(letter);
  if (online !== null) head.append(el("i", `online${online ? "" : " off"}`));
  const body = el("span", "friend-body");
  body.append(el("strong", "", person.name), el("small", online ? "on" : "", detail));
  const buttons = el("span", "actions");
  for (const [label, cls, onClick] of actions) {
    const b = el("button", cls, label);
    b.addEventListener("click", () => run(b, async () => {
      try {
        await onClick();
      } catch (e) {
        toast(networkError(e), true);
      }
      await renderFriends();
    }));
    buttons.append(b);
  }
  row.append(head, body, buttons);
  face(person.uuid.replaceAll("-", "")).then((src) => {
    if (!src) return;
    const img = el("img", "face");
    img.alt = "";
    img.src = src;
    letter.replaceWith(img);
  });
  return row;
}

let friendsTimer = null;
async function renderFriends() {
  clearTimeout(friendsTimer);
  if (!view?.accounts.length) {
    renderOnline(null, "Sign in with Microsoft to see your friends online.");
    $("friends-error").textContent = "Sign in with Microsoft to see your Rago friends.";
    $("friends-error").hidden = false;
    $("friend-add").disabled = true;
    return;
  }
  $("friend-add").disabled = false;
  let data;
  try {
    data = await invoke("friends");
    $("friends-error").hidden = true;
  } catch (e) {
    $("friends-error").textContent = networkError(e);
    $("friends-error").hidden = false;
    renderOnline(null, networkError(e));
    scheduleFriends();
    return;
  }
  renderOnline(data.friends.filter((f) => f.online));
  const online = data.friends.filter((f) => f.online).length;
  $("friends-count").hidden = online === 0;
  $("friends-count").textContent = online;
  $("friends-title").textContent = data.friends.length ? `Friends · ${online} online` : "Friends";
  $("friend-list").replaceChildren(...data.friends.map((f) =>
    friendRow(f, f.online ? "Playing with Rago" : "Offline", f.online, [["Remove", "link danger", () => invoke("friend_remove", { uuid: f.uuid })]])));
  $("friends-empty").hidden = data.friends.length > 0;
  $("received-group").hidden = !data.received.length;
  $("received-list").replaceChildren(...data.received.map((p) =>
    friendRow(p, "Wants to be your friend", null, [
      ["Accept", "primary", () => invoke("friend_answer", { uuid: p.uuid, accept: true })],
      ["Decline", "secondary", () => invoke("friend_answer", { uuid: p.uuid, accept: false })],
    ])));
  $("sent-group").hidden = !data.sent.length;
  $("sent-list").replaceChildren(...data.sent.map((p) =>
    friendRow(p, "Request sent", null, [["Cancel", "link danger", () => invoke("friend_remove", { uuid: p.uuid })]])));
  scheduleFriends();
}

// the lists follow who comes and goes while home or the friends page is open; nothing runs during a game
function scheduleFriends() {
  clearTimeout(friendsTimer);
  if ((page === "friends" || page === "home") && !playing) friendsTimer = setTimeout(renderFriends, 60000);
}

/** The home card's online friends: as many as the card has room for, then a link to the others. */
let lastOnline = null;
function renderOnline(online, message = "") {
  lastOnline = online;
  const list = $("online-list");
  const shown = online ? online.slice(0, 12) : [];
  const count = online ? online.length : 0;
  $("home-friends-count").hidden = count === 0;
  $("home-friends-count").textContent = count;
  $("online-empty").textContent = message || "None of your friends is playing with Rago right now.";
  $("online-empty").hidden = shown.length > 0;
  list.replaceChildren(...shown.map((f) => {
    const row = el("button", "buddy");
    row.title = `${f.name}: open your friends`;
    const head = el("span", "head");
    const letter = el("span", "face letter", f.name.slice(0, 1).toUpperCase());
    head.append(letter, el("i", "online-dot"));
    const body = el("span", "online-body");
    body.append(el("strong", "", f.name), el("small", "", f.role && f.role !== "user" ? `Online · ${f.role[0].toUpperCase()}${f.role.slice(1)}` : "Online · Rago"));
    row.append(head, body);
    row.addEventListener("click", () => showPage("friends"));
    face(f.uuid.replaceAll("-", "")).then((src) => {
      if (!src) return;
      const img = el("img", "face");
      img.alt = "";
      img.src = src;
      letter.replaceWith(img);
    });
    return row;
  }));
  fitOnline();
}

/** Keeps the rows that fit in the card and says how many others are online. */
function fitOnline() {
  const list = $("online-list");
  const box = $("feed-friends");
  if (box.hidden || !lastOnline) return;
  list.querySelector(".more")?.remove();
  const rows = [...list.children];
  for (const r of rows) r.hidden = false;
  // layout positions, not on-screen ones: the rows may still be rising in
  const room = box.clientHeight;
  const fits = (r, below) => r.offsetTop + r.offsetHeight <= room - below + 1;
  let shown = rows.filter((r) => fits(r, 0)).length;
  const others = () => lastOnline.length - shown;
  if (others() > 0) {
    // keep room under the last row for the link (one short line)
    shown = rows.filter((r) => fits(r, 28)).length;
    rows.forEach((r, i) => (r.hidden = i >= shown));
    const more = el("button", "buddy more", `+${others()} more online`);
    more.addEventListener("click", () => showPage("friends"));
    list.append(more);
  }
}
new ResizeObserver(() => requestAnimationFrame(fitOnline)).observe($("feed-friends"));

$("friend-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const name = $("friend-name").value.trim();
  if (!name) return;
  run($("friend-add"), async () => {
    try {
      const now = await invoke("friend_add", { name });
      toast(now ? `You and ${name} are friends now.` : `Friend request sent to ${name}.`);
      $("friend-name").value = "";
    } catch (e) {
      toast(networkError(e), true);
    }
    await renderFriends();
  });
});

let pointsTimer = null;
async function renderPoints() {
  clearTimeout(pointsTimer);
  if (!view?.accounts.length) {
    $("points").hidden = true;
    return;
  }
  if (!playing && !document.hidden) {
    const p = await invoke("points").catch(() => null);
    $("points").hidden = p === null;
    if (p !== null) setText($("points-value"), p.toLocaleString("en-US"));
  }
  pointsTimer = setTimeout(renderPoints, 300000);
}

// presets and the optimizer

for (const b of document.querySelectorAll("[data-preset]")) {
  b.addEventListener("click", async () => {
    try {
      await invoke("apply_preset", { name: b.dataset.preset });
      toast(`${PRESET_NAMES[b.dataset.preset]} profile applied. It takes effect on the next launch.`);
    } catch (e) {
      toast(String(e), true);
    }
    await refresh();
  });
}

$("optimize").addEventListener("click", async () => {
  try {
    await invoke("optimize");
    toast(view.windows ? "Optimized: Competitive profile with OptiFine's fast settings, tuned Java and high priority." : "Optimized: Competitive profile with OptiFine's fast settings and tuned Java.");
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
  if (!mods.length) list.append(el("p", "muted small-text", "No mods yet: Rago installs itself on the first launch."));
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
    body.append(el("strong", "", m.file), el("small", "", m.kind === "rago" ? "The client, always on" : m.kind === "optifine" ? "Graphics" : "Forge mod"));
    row.append(ic, body);
    if (m.kind !== "rago") {
      const sw = el("input", "switch");
      sw.type = "checkbox";
      sw.checked = m.enabled;
      sw.setAttribute("aria-label", `Enable ${m.file}`);
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
    if (view.engine === b.dataset.engine) toast(b.dataset.engine === "rago" ? "Rago loader selected: the next launch prepares Java 21." : "Forge selected for the next launch.");
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
    toast("Arguments saved.");
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
      if (version) toast(`Java ${version} selected.`);
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
      if (name) toast(`${name} added.`);
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
$("discord").addEventListener("click", () => invoke("open_discord").catch((e) => toast(String(e), true)));
$("open-crash").addEventListener("click", () => crashReport && invoke("open_crash_report", { path: crashReport }));

$("import").addEventListener("click", () =>
  run($("import"), async () => {
    try {
      const copied = await invoke("import_official");
      toast(copied.length ? `Imported ${copied.length} item${copied.length === 1 ? "" : "s"} (options, servers, packs).` : "Nothing new to import.");
      renderServers();
    } catch (e) {
      toast(String(e), true);
    }
  }));

$("repair").addEventListener("click", () =>
  run($("repair"), async () => {
    try {
      await invoke("repair");
      toast("The game and Java will be downloaded again on the next launch.");
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
  const frag = document.createDocumentFragment();
  for (const l of batch) if (matches(l)) frag.append(lineNode(l));
  out.append(frag);
  while (out.childElementCount > MAX_LINES) out.firstElementChild.remove();
  $("console-empty").hidden = true;
  if ($("console-follow").checked) out.scrollTop = out.scrollHeight;
}

// the filter redraws up to 4000 lines: wait until typing pauses
let filterTimer = null;
$("console-filter").addEventListener("input", () => {
  clearTimeout(filterTimer);
  filterTimer = setTimeout(renderConsole, 120);
});
$("console-follow").addEventListener("change", (e) => e.target.checked && ($("console-out").scrollTop = $("console-out").scrollHeight));
$("console-copy").addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(lines.filter(matches).join("\n"));
    toast("Console copied.");
  } catch {
    toast("Copy failed: select the text and press Ctrl+C.", true);
  }
});

// events from the game

listen("progress", ({ payload }) => {
  if (payload.stage === "files") {
    setStatus(`Checking Minecraft files: ${payload.done} of ${payload.total}` + (payload.megabytes ? ` (${payload.megabytes} MB downloaded)` : ""), false, false);
    setProgress(payload.total ? payload.done / payload.total : 0);
    setState(`Files ${payload.done}/${payload.total}`, "busy");
  } else {
    setStatus(STAGES[payload.stage] ?? "…");
    setState(STAGES[payload.stage] ?? "…", "busy");
    setProgress("busy");
  }
});
listen("playing", () => {
  setPlaying(true);
  setProgress(null);
  setStatus("Have fun!");
  setState("In game", "playing");
  $("play-text").textContent = "In game";
  lines.length = 0;
  renderConsole();
  if (view?.showConsole) showPage("console");
});
listen("console", ({ payload }) => addLines(payload));
listen("closed", ({ payload }) => {
  setPlaying(false);
  pollStats();
  renderPoints();
  renderFriends();
  $("play-text").textContent = "Play";
  const ok = payload.code === 0 || payload.code === null;
  crashReport = payload.crashReport;
  $("after-crash").hidden = ok;
  $("open-crash").hidden = !crashReport;
  setStatus(ok ? "" : `The game closed with an error (code ${payload.code}).`, !ok);
  if (ok) setState("Ready");
  refresh();
  renderServers();
});

// start

character.init();
const started = refresh();
started.then(async () => {
  setState(view.playing ? "In game" : "Ready", view.playing ? "playing" : "");
  character.update();
  // a game still running (the window was reloaded): its output so far
  lines.push(...(await invoke("console")));
  renderConsole();
  renderServers();
  pollStats();
  renderPoints();
  renderFriends();
});
showFeed("friends");
renderNews();
renderNotes();
started.then(() => checkUpdate(true), () => checkUpdate(false));
setInterval(() => checkUpdate(false), UPDATE_EVERY);
