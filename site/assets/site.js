// Copy buttons (the Pix key): copy the text, or select it when the browser refuses.
document.querySelectorAll("button[data-copy]").forEach((button) => {
  button.addEventListener("click", async () => {
    const target = document.getElementById(button.dataset.copy);
    const label = button.textContent;
    try {
      await navigator.clipboard.writeText(target.textContent.trim());
      button.textContent = button.dataset.done;
      setTimeout(() => { button.textContent = label; }, 2000);
    } catch {
      const range = document.createRange();
      range.selectNodeContents(target);
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
    }
  });
});

// Screenshot theme switch: starts on the visitor's theme, then follows the buttons.
document.querySelectorAll(".features").forEach((section) => {
  const buttons = section.querySelectorAll(".theme-switch button");
  const show = (theme) => {
    section.dataset.shots = theme;
    buttons.forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.shots === theme)));
  };
  show(window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  buttons.forEach((b) => b.addEventListener("click", () => show(b.dataset.shots)));
});
