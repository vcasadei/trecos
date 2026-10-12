// The static Pix "copia e cola" code (BR Code, Banco Central do Brasil) for a
// Pix key with no fixed amount, so the payer types the value.

const field = (id, value) => id + String(value.length).padStart(2, "0") + value;

// CRC-16/CCITT-FALSE (polynomial 0x1021, start 0xFFFF), as the BR Code requires.
export function crc16(text) {
  let crc = 0xffff;
  for (const byte of Buffer.from(text, "utf8")) {
    crc ^= byte << 8;
    for (let bit = 0; bit < 8; bit++) {
      crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, "0");
}

// Plain ASCII, upper case, at most `max` characters: what payer banks display.
const clean = (text, max) =>
  text.normalize("NFD").replace(/[^\x20-\x7E]/g, "").toUpperCase().slice(0, max);

export function pixPayload({ key, name, city }) {
  const body =
    field("00", "01") +
    field("26", field("00", "br.gov.bcb.pix") + field("01", key)) +
    field("52", "0000") +
    field("53", "986") +
    field("58", "BR") +
    field("59", clean(name, 25)) +
    field("60", clean(city, 15)) +
    field("62", field("05", "***")) +
    "6304";
  return body + crc16(body);
}
