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
