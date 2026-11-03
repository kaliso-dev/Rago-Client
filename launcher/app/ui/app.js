// The launcher window: calls the Rust commands in src/main.rs and shows their events.
const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

const $ = (id) => document.getElementById(id);
const STAGES = {
  version: "Préparation…",
  natives: "Préparation du jeu…",
  java: "Vérification de Java…",
  rago: "Mise à jour de Rago…",
};
const SIZES = ["1280x720", "1600x900", "1920x1080"];
const MAX_LINES = 4000;

let view = null;
let update = null;
let crashReport = null;
const faces = {};
const lines = [];

function setStatus(text, error = false) {
  $("status-text").textContent = text;
  $("status-text").classList.toggle("error", error);
}

function setProgress(fraction) {
  $("track").hidden = fraction === null;
  if (fraction !== null) $("fill").style.width = `${Math.round(fraction * 100)}%`;
}

let toastTimer = null;
function toast(text, error = false) {
  const t = $("toast");
  t.textContent = text;
  t.classList.toggle("error", error);
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), error ? 6000 : 3000);
}

function gb(mb) {
  const g = mb / 1024;
  return `${Number.isInteger(g) ? g : g.toFixed(1).replace(".", ",")} Go`;
}

// pages

function showPage(name) {
  for (const p of document.querySelectorAll(".page")) p.hidden = p.id !== `page-${name}`;
  for (const t of document.querySelectorAll(".tab")) {
    if (t.dataset.page === name) t.setAttribute("aria-current", "page");
    else t.removeAttribute("aria-current");
  }
  if (name === "settings") litSection();
  if (name === "console") {
    $("console-dot").hidden = true;
    renderConsole();
  }
}

function goto(section) {
  showPage("settings");
  $(section).scrollIntoView({ block: "start" });
}

for (const t of document.querySelectorAll(".tab")) t.addEventListener("click", () => showPage(t.dataset.page));
for (const b of document.querySelectorAll("[data-goto]")) b.addEventListener("click", () => goto(b.dataset.goto));

// the settings section on screen, lit in the side list
function litSection() {
  const top = $("settings-scroll").getBoundingClientRect().top + 40;
  let current = "perf";
  for (const g of document.querySelectorAll(".group")) if (g.getBoundingClientRect().top <= top) current = g.id;
  const atEnd = $("settings-scroll").scrollTop + $("settings-scroll").clientHeight >= $("settings-scroll").scrollHeight - 2;
  if (atEnd) current = "tools";
  for (const b of document.querySelectorAll(".subnav button")) b.classList.toggle("current", b.dataset.goto === current);
}
$("settings-scroll").addEventListener("scroll", litSection);

// accounts

async function face(uuid) {
  if (!(uuid in faces)) faces[uuid] = await invoke("skin_face", { uuid }).catch(() => null);
  return faces[uuid];
}

function menuButton(text, onClick, className = "") {
  const b = document.createElement("button");
  b.textContent = text;
  if (className) b.className = className;
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
  if (!selected) return;
  $("account-name").textContent = selected.name;
  $("face-letter").textContent = selected.name.slice(0, 1).toUpperCase();
  for (const a of view.accounts) {
    const b = menuButton(a.name, () => run(b, async () => {
      await invoke("select_account", { uuid: a.uuid });
      $("accounts").open = false;
    }), a.uuid === selected.uuid ? "current" : "");
    menu.append(b);
  }
  menu.append(document.createElement("hr"));
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
}

// state

function render() {
  $("version").textContent = `v${view.version}`;
  renderAccounts();
  const signedIn = view.accounts.length > 0;
  $("play").disabled = !signedIn || $("play").dataset.running === "1";
  if (!signedIn && !$("status-text").textContent) {
    setStatus(view.signInReady ? "Connecte-toi pour jouer." : "La connexion Microsoft arrive bientôt : Mojang doit encore autoriser l'application Rago.");
  }
  const rago = view.rago?.match(/^rago-(.+?)\+/);
  $("eyebrow").textContent = `Minecraft 1.8.9 · Forge${rago ? ` · Rago ${rago[1]}` : ""}`;
  $("machine").textContent = [view.systemMemoryMb ? `${gb(Math.round(view.systemMemoryMb / 512) * 512)} RAM` : "", `${view.cpuThreads} threads`].filter(Boolean).join("\n");

  // home
  $("home-memory").textContent = gb(view.memoryMb);
  $("home-memory-hint").textContent = view.memoryMb === view.recommendedMemoryMb ? "La valeur recommandée pour ton PC." : `Recommandé pour ton PC : ${gb(view.recommendedMemoryMb)}.`;
  $("optifine-text").textContent = view.optifine
    ? `${view.optifine} est installé.`
    : "OptiFine ne peut pas être livré avec Rago. Télécharge-le sur son site, puis ajoute le fichier ici.";
  $("optifine-remove").hidden = !view.optifine;

  // performance
  $("memory").max = view.maxMemoryMb;
  $("memory").value = view.memoryMb;
  $("memory-value").textContent = gb(view.memoryMb);
  $("memory-hint").textContent = `Recommandé : ${gb(view.recommendedMemoryMb)}${view.systemMemoryMb ? ` (ton PC a ${gb(Math.round(view.systemMemoryMb / 512) * 512)})` : ""}. Plus que nécessaire rend chaque nettoyage de la mémoire plus long.`;
  $("tuned-jvm").checked = view.tunedJvm;
  $("high-priority").checked = view.highPriority;
  $("dedicated-gpu").checked = view.dedicatedGpu;
  for (const s of document.querySelectorAll("[data-windows]")) s.hidden = !view.windows;

  // game
  const size = view.width && view.height ? `${view.width}x${view.height}` : "";
  const custom = size && !SIZES.includes(size);
  if ($("size-preset").value !== "custom" || !custom) $("size-preset").value = custom ? "custom" : size;
  $("custom-size").hidden = $("size-preset").value !== "custom";
  if (document.activeElement !== $("width")) $("width").value = view.width ?? "";
  if (document.activeElement !== $("height")) $("height").value = view.height ?? "";
  $("fullscreen").checked = view.fullscreen;
  $("auto-update").checked = view.autoUpdate;

  // java
  $("java-text").textContent = view.javaPath ? view.javaPath : "Java 8 de Temurin, installé et mis à jour par le launcher.";
  $("java-text").classList.toggle("path", !!view.javaPath);
  $("java-reset").hidden = !view.javaPath;
  if (document.activeElement !== $("jvm-args")) $("jvm-args").value = view.jvmArgs;

  // launcher
  $("on-launch").value = view.onLaunch;
  $("show-console").checked = view.showConsole;

  // tools
  $("import").disabled = !view.officialFound;
  $("game-dir").textContent = view.gameDir;
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

$("play").addEventListener("click", () => {
  $("after-crash").hidden = true;
  $("play").dataset.running = "1";
  $("play").textContent = "Lancement…";
  run($("play"), async () => {
    setStatus("Connexion…");
    try {
      await invoke("play");
    } finally {
      $("play").dataset.running = "";
      $("play").textContent = "Jouer";
    }
  });
});

// settings

$("memory").addEventListener("input", (e) => ($("memory-value").textContent = gb(Number(e.target.value))));
$("memory").addEventListener("change", (e) => save("memoryMb", Number(e.target.value)));
for (const [id, key] of [["tuned-jvm", "tunedJvm"], ["high-priority", "highPriority"], ["dedicated-gpu", "dedicatedGpu"], ["fullscreen", "fullscreen"], ["auto-update", "autoUpdate"], ["show-console", "showConsole"]]) {
  $(id).addEventListener("change", (e) => save(key, e.target.checked));
}
$("on-launch").addEventListener("change", (e) => save("onLaunch", e.target.value));

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

for (const b of document.querySelectorAll("[data-preset]")) {
  b.addEventListener("click", async () => {
    try {
      await invoke("apply_preset", { name: b.dataset.preset });
      for (const o of document.querySelectorAll("[data-preset]")) o.classList.toggle("done", o.dataset.preset === b.dataset.preset);
      toast(`Préréglage « ${b.textContent} » appliqué. Il prend effet au prochain lancement.`);
    } catch (e) {
      toast(String(e), true);
    }
  });
}

// OptiFine and tools

$("optifine-page").addEventListener("click", () => invoke("open_optifine_page"));
$("optifine-add").addEventListener("click", () =>
  run($("optifine-add"), async () => {
    const name = await invoke("add_optifine");
    if (name) setStatus(`${name} ajouté.`);
  }));
$("optifine-remove").addEventListener("click", () => run($("optifine-remove"), () => invoke("remove_optifine")));

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

$("update-go").addEventListener("click", () =>
  run($("update-go"), async () => {
    $("update-text").textContent = "Téléchargement de la mise à jour…";
    await invoke("install_update", { update });
  }));

// console

function lineClass(line) {
  if (/\b(ERROR|FATAL)\b|Exception|^\s+at /.test(line)) return "error";
  if (/\bWARN\b/.test(line)) return "warn";
  return "";
}

function lineNode(line) {
  const d = document.createElement("div");
  d.textContent = line;
  const c = lineClass(line);
  if (c) d.className = c;
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
  if ($("page-console").hidden) {
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

// events

listen("progress", ({ payload }) => {
  if (payload.stage === "files") {
    setStatus(`Vérification des fichiers de Minecraft : ${payload.done} sur ${payload.total}` + (payload.megabytes ? ` (${payload.megabytes} Mo téléchargés)` : ""));
    setProgress(payload.total ? payload.done / payload.total : 0);
  } else {
    setStatus(STAGES[payload.stage] ?? "…");
    setProgress(null);
  }
});
listen("playing", () => {
  setProgress(null);
  setStatus("Bon jeu !");
  $("play").textContent = "En jeu";
  lines.length = 0;
  renderConsole();
  if (view?.showConsole) showPage("console");
});
listen("console", ({ payload }) => addLines(payload));
listen("closed", ({ payload }) => {
  $("play").textContent = "Jouer";
  const ok = payload.code === 0 || payload.code === null;
  crashReport = payload.crashReport;
  $("after-crash").hidden = ok;
  $("open-crash").hidden = !crashReport;
  setStatus(ok ? "" : `Le jeu s'est fermé sur une erreur (code ${payload.code}).`, !ok);
});

refresh().then(async () => {
  // a game still running (the window was reloaded): its output so far
  lines.push(...(await invoke("console")));
  renderConsole();
});
invoke("check_update")
  .then((u) => {
    update = u;
    if (!u) return;
    $("update-text").textContent = `Une nouvelle version du launcher est prête : v${u.version}.`;
    $("update").hidden = false;
  })
  .catch(() => {});
