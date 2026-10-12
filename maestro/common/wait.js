// Waits SECONDS seconds, for flows that need real time to pass (lock timeouts).
var end = Date.now() + Number(SECONDS) * 1000;
while (Date.now() < end) { /* busy-wait: Maestro's JS has no sleep */ }
