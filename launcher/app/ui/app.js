// The launcher window: calls the Rust commands in src/main.rs and shows their events.
const { invoke } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

const $ = (id) => document.getElementById(id);
const STAGES = {
  version: "Préparation…",
  natives: "Préparation du jeu…",
  java: "Installation de Java…",
  rago: "Mise à jour de Rago…",
};

let view = null;

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

function render() {
  const signedIn = !!view.account;
  $("account").hidden = !signedIn;
  if (signedIn) {
    $("account-name").textContent = view.account.name;
    $("avatar").textContent = view.account.name.slice(0, 1).toUpperCase();
  }
  $("sign-in").hidden = signedIn;
  $("account-text").textContent = signedIn
    ? `Connecté en tant que ${view.account.name}.`
    : view.signInReady
      ? "Connecte-toi avec le compte qui possède Minecraft. Ton mot de passe reste chez Microsoft."
      : "La connexion Microsoft arrive bientôt : Mojang doit encore autoriser l'application Rago.";
  $("sign-in").disabled = !view.signInReady;
  $("play").disabled = !signedIn;
  if (!signedIn && !$("status-text").textContent) setStatus("Connecte-toi pour jouer.");
  $("optifine-text").textContent = view.optifine
    ? `${view.optifine} est installé.`
    : "OptiFine ne peut pas être livré avec Rago. Télécharge-le sur son site, puis ajoute le fichier ici.";
  $("memory").value = view.memoryMb;
  $("memory-value").textContent = memoryText(view.memoryMb);
}

async function refresh() {
  view = await invoke("state");
  render();
}

async function run(button, task) {
  button.disabled = true;
  try {
    await task();
  } catch (e) {
    setProgress(null);
    setStatus(String(e), true);
  } finally {
    await refresh();
  }
}

$("sign-in").addEventListener("click", () =>
  run($("sign-in"), async () => {
    setStatus("Termine la connexion dans ton navigateur…");
    const account = await invoke("sign_in");
    setStatus(`Bienvenue ${account.name} !`);
  }));

$("sign-out").addEventListener("click", () => run($("sign-out"), () => invoke("sign_out").then(() => setStatus(""))));

$("play").addEventListener("click", () =>
  run($("play"), async () => {
    setStatus("Connexion…");
    await invoke("play");
  }));

$("optifine-page").addEventListener("click", () => invoke("open_optifine_page"));
$("optifine-add").addEventListener("click", () =>
  run($("optifine-add"), async () => {
    const name = await invoke("add_optifine");
    if (name) setStatus(`${name} ajouté.`);
  }));
$("folder").addEventListener("click", () => invoke("open_folder"));

$("memory").addEventListener("input", (e) => ($("memory-value").textContent = memoryText(Number(e.target.value))));
$("memory").addEventListener("change", (e) => invoke("set_memory", { mb: Number(e.target.value) }));

listen("progress", ({ payload }) => {
  if (payload.stage === "files") {
    setStatus(`Téléchargement des fichiers de Minecraft : ${payload.done} sur ${payload.total} (${payload.megabytes} Mo)`);
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
  setStatus(payload === 0 || payload === null ? "" : `Le jeu s'est fermé avec une erreur (code ${payload}).`, payload !== 0 && payload !== null);
});

refresh();
