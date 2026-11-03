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

let view = null;
let update = null;
let crashReport = null;
const faces = {};

function setStatus(text, error = false) {
  $("status-text").textContent = text;
  $("status-text").classList.toggle("error", error);
}

function setProgress(fraction) {
  $("track").hidden = fraction === null;
  if (fraction !== null) $("fill").style.width = `${Math.round(fraction * 100)}%`;
}

function memoryText(mb) {
  const gb = mb / 1024;
  return `${Number.isInteger(gb) ? gb : gb.toFixed(1).replace(".", ",")} Go`;
}

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

function render() {
  $("version").textContent = `v${view.version}`;
  renderAccounts();
  const signedIn = view.accounts.length > 0;
  $("play").disabled = !signedIn || $("play").dataset.running === "1";
  if (!signedIn && !$("status-text").textContent) {
    setStatus(view.signInReady ? "Connecte-toi pour jouer." : "La connexion Microsoft arrive bientôt : Mojang doit encore autoriser l'application Rago.");
  }
  $("installed").textContent = view.rago ? `Installé : ${view.rago.replace(/\.jar$/, "")}` : "";
  $("optifine-text").textContent = view.optifine
    ? `${view.optifine} est installé.`
    : "OptiFine ne peut pas être livré avec Rago. Télécharge-le sur son site, puis ajoute le fichier ici.";
  $("optifine-remove").hidden = !view.optifine;
  $("memory").value = view.memoryMb;
  $("memory-value").textContent = memoryText(view.memoryMb);
  $("on-launch").value = view.onLaunch;
  $("import").disabled = !view.officialFound;
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

$("optifine-page").addEventListener("click", () => invoke("open_optifine_page"));
$("optifine-add").addEventListener("click", () =>
  run($("optifine-add"), async () => {
    const name = await invoke("add_optifine");
    if (name) setStatus(`${name} ajouté.`);
  }));
$("optifine-remove").addEventListener("click", () => run($("optifine-remove"), () => invoke("remove_optifine")));

for (const b of document.querySelectorAll("[data-folder]")) {
  b.addEventListener("click", () => invoke("open_folder", { which: b.dataset.folder }).catch((e) => setStatus(String(e), true)));
}
$("open-logs").addEventListener("click", () => invoke("open_folder", { which: "logs" }));
$("open-crash").addEventListener("click", () => crashReport && invoke("open_crash_report", { path: crashReport }));

$("import").addEventListener("click", () =>
  run($("import"), async () => {
    const copied = await invoke("import_official");
    setStatus(copied.length ? `Importé : ${copied.length} élément${copied.length > 1 ? "s" : ""} (options, serveurs, packs).` : "Rien de nouveau à importer.");
  }));

$("repair").addEventListener("click", () =>
  run($("repair"), async () => {
    await invoke("repair");
    setStatus("Le jeu et Java seront retéléchargés au prochain lancement. Tes mondes et options sont gardés.");
  }));

$("memory").addEventListener("input", (e) => ($("memory-value").textContent = memoryText(Number(e.target.value))));
$("memory").addEventListener("change", (e) => invoke("set_memory", { mb: Number(e.target.value) }));
$("on-launch").addEventListener("change", (e) => invoke("set_on_launch", { value: e.target.value }));

$("update-go").addEventListener("click", () =>
  run($("update-go"), async () => {
    $("update-text").textContent = "Téléchargement de la mise à jour…";
    await invoke("install_update", { update });
  }));

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
});
listen("closed", ({ payload }) => {
  $("play").textContent = "Jouer";
  const ok = payload.code === 0 || payload.code === null;
  crashReport = payload.crashReport;
  $("after-crash").hidden = ok;
  $("open-crash").hidden = !crashReport;
  setStatus(ok ? "" : `Le jeu s'est fermé sur une erreur (code ${payload.code}).`, !ok);
});

refresh();
invoke("check_update")
  .then((u) => {
    update = u;
    if (!u) return;
    $("update-text").textContent = `Une nouvelle version du launcher est prête : v${u.version}.`;
    $("update").hidden = false;
  })
  .catch(() => {});
