import {
  PDFDocument,
  type PDFFont,
  rgb,
  StandardFonts,
} from "npm:pdf-lib@1.17.1";
import fontkit from "npm:@pdf-lib/fontkit@1.1.1";
import { Buffer } from "node:buffer";
import {
  type Bill,
  money,
  receiptItems,
  receiptLines,
  receiptNumber,
  receiptSections,
} from "./receipt.ts";
import { logoBase64 } from "./brand.ts";
let fontBytes: Uint8Array | undefined;

/** A4 electronic receipt with the exact app launcher logo. */
export async function receiptPdf(b: Bill): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  const regular = await doc.embedFont(StandardFonts.Helvetica);
  const bold = await doc.embedFont(StandardFonts.HelveticaBold);
  const latin = new Set(regular.getCharacterSet());
  let unicode = regular;
  if (
    receiptLines(b).some((line) =>
      Array.from(line).some((c) => !latin.has(c.codePointAt(0)!))
    )
  ) {
    doc.registerFontkit(fontkit);
    if (!fontBytes) {
      const r = await fetch(
        "https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-4.4_r1/data/fonts/DroidSansFallback.ttf",
        { signal: AbortSignal.timeout(30000) },
      );
      if (!r.ok) throw Error("Receipt font unavailable");
      fontBytes = new Uint8Array(await r.arrayBuffer());
    }
    unicode = await doc.embedFont(fontBytes, { subset: true });
  }
  const fontFor = (c: string, strong = false): PDFFont =>
    latin.has(c.codePointAt(0)!) ? (strong ? bold : regular) : unicode;
  const width = (s: string, size: number, strong = false) =>
    Array.from(s).reduce(
      (w, c) => w + fontFor(c, strong).widthOfTextAtSize(c, size),
      0,
    );
  const wrap = (s: string, max: number, size: number, strong = false) => {
    const out: string[] = [];
    let part = "";
    for (const c of s) {
      if (c === "\n") {
        out.push(part);
        part = "";
        continue;
      }
      if (part && width(part + c, size, strong) > max) {
        out.push(part);
        part = "";
      }
      part += c;
    }
    out.push(part);
    return out;
  };
  const W = 595, H = 842, M = 36, C = W - M * 2;
  const ink = rgb(.08, .11, .13),
    muted = rgb(.43, .48, .51),
    line = rgb(.86, .89, .90),
    lime = rgb(.78, .96, .42),
    soft = rgb(.96, .97, .97);
  const photo = await doc.embedJpg(Buffer.from(logoBase64, "base64"));
  let page = doc.addPage([W, H]);
  let y = 0;
  const box = (x: number, top: number, w: number, h: number, color = soft) =>
    page.drawRectangle({ x, y: H - top - h, width: w, height: h, color });
  const text = (
    s: string,
    x: number,
    top: number,
    size = 10,
    strong = false,
    color = ink,
  ) => {
    let cursor = x;
    for (const c of s) {
      const font = fontFor(c, strong);
      page.drawText(c, { x: cursor, y: H - top - size, size, font, color });
      cursor += font.widthOfTextAtSize(c, size);
    }
  };
  const rule = (top: number) =>
    page.drawLine({
      start: { x: M, y: H - top },
      end: { x: W - M, y: H - top },
      color: line,
      thickness: .6,
    });
  const brand = (top: number) => {
    box(0, top, W, 46, ink);
    text("CTFLY", M, top + 8, 20, true, lime);
    text(
      "GAMING CAFE / MALAYSIA",
      M + 88,
      top + 15,
      8,
      false,
      rgb(.83, .87, .88),
    );
    text(
      "0173707898",
      W - M - width("0173707898", 10),
      top + 13,
      10,
      false,
      rgb(1, 1, 1),
    );
  };
  const ensure = (height: number) => {
    if (y + height <= 768) return;
    page = doc.addPage([W, H]);
    brand(0);
    text("RECEIPT CONTINUED", M, 61, 10, true);
    y = 87;
  };
  const heading = (s: string) => {
    ensure(26);
    text(s.toUpperCase(), M, y, 9, true, muted);
    y += 23;
  };
  const row = (label: string, value: string, strong = false) => {
    const parts = wrap(value, C - 116, 10, strong);
    const height = Math.max(18, parts.length * 13 + 5);
    ensure(height);
    text(label, M, y, 9, false, muted);
    parts.forEach((p, i) => text(p, M + 116, y + i * 13, 10, strong));
    y += height;
  };
  const amount = (label: string, value: string) => {
    const parts = wrap(label, C - 112, 10);
    const h = parts.length * 13 + 7;
    ensure(h);
    parts.forEach((p, i) => text(p, M, y + i * 13, 10));
    text(value, W - M - width(value, 10), y, 10, true);
    y += h;
  };
  box(0, 0, W, 96, ink);
  page.drawImage(photo, { x: M, y: H - 73, width: 52, height: 52 });
  text("CTFLY", M + 70, 27, 25, true, rgb(1, 1, 1));
  text("GAMING CAFE / MALAYSIA", M + 71, 59, 9, false, rgb(.73, .80, .82));
  text(
    "0173707898",
    W - M - width("0173707898", 11),
    40,
    11,
    false,
    rgb(1, 1, 1),
  );
  text(
    b.kind === "Order"
      ? "Order receipt"
      : b.kind === "Event"
      ? "Event receipt"
      : "Booking receipt",
    M,
    124,
    25,
    true,
  );
  const badge = b.environment === "test"
    ? "TEST / " + b.status.toUpperCase()
    : b.status.toUpperCase();
  const badgeWidth = Math.max(66, width(badge, 9, true) + 24);
  box(
    W - M - badgeWidth,
    131,
    badgeWidth,
    24,
    b.status === "Void" ? rgb(1, .91, .85) : lime,
  );
  text(badge, W - M - badgeWidth + 12, 138, 9, true);
  y = 163;
  if (b.environment === "test") {
    box(M, y, C, 24, rgb(1, .96, .87));
    text(
      "TEST RECEIPT - NO MONEY CHARGED",
      M + 12,
      y + 7,
      9,
      true,
      rgb(.45, .32, .12),
    );
    y += 36;
  }
  const sections = receiptSections(b);
  for (const r of sections[0].rows.filter((r) => r.label !== "Status")) {
    row(r.label, r.value);
  }
  y += 12;
  rule(y);
  y += 17;
  heading(sections[1].title);
  const priority = [
    "Activity",
    "Space / item",
    "Start",
    "End",
    "Duration",
    "Players",
    "Zone",
    "Hourly rate",
    "Game",
    "Team",
  ];
  const fields = [...sections[1].rows].sort((a, b) =>
    priority.indexOf(a.label) - priority.indexOf(b.label)
  );
  for (let i = 0; i < fields.length; i += 2) {
    const pair = fields.slice(i, i + 2);
    const wrapped = pair.map((r) => wrap(r.value, (C - 42) / 2, 11, true));
    const h = Math.max(...wrapped.map((v) => v.length)) * 14 + 25;
    ensure(h);
    box(M, y, C, h, soft);
    pair.forEach((r, j) => {
      const x = M + 14 + j * (C / 2);
      text(r.label.toUpperCase(), x, y + 7, 8, false, muted);
      wrapped[j].forEach((p, n) => text(p, x, y + 20 + n * 14, 11, true));
    });
    y += h;
  }
  y += 17;
  heading("Charges");
  const items = receiptItems(b);
  for (
    const r of items.length ? items : [{
      label: String(b.details?.activity ?? b.kind),
      value: money(b.subtotal),
    }]
  ) amount(r.label, r.value);
  rule(y);
  y += 10;
  amount("Subtotal", money(b.subtotal));
  amount(
    "Discount" + (b.details?.promo_code ? " / " + b.details.promo_code : ""),
    b.discount ? "- " + money(b.discount) : money(0),
  );
  ensure(62);
  y += 5;
  box(M, y, C, 46, ink);
  text(
    b.status === "Void"
      ? "VOIDED TOTAL"
      : b.environment === "test"
      ? "TEST TOTAL"
      : "TOTAL PAID",
    M + 15,
    y + 17,
    9,
    true,
    rgb(1, 1, 1),
  );
  const total = money(b.total);
  text(total, W - M - 15 - width(total, 24, true), y + 9, 24, true, lime);
  y += 64;
  heading("Payment details");
  for (const r of sections[2].rows) row(r.label, r.value);
  y += 12;
  for (const [i, p] of doc.getPages().entries()) {
    page = p;
    rule(786);
    text("THANK YOU FOR PLAYING WITH US.", M, 795, 8, true);
    text(
      "Malaysia (UTC+8) / MYR  |  Keep this receipt for your booking.",
      M,
      810,
      8,
      false,
      muted,
    );
    text(
      "Page " + (i + 1),
      W - M - width("Page " + (i + 1), 8),
      810,
      8,
      false,
      muted,
    );
  }
  doc.setTitle("CTFLY receipt " + receiptNumber(b));
  doc.setAuthor("CTFLY Gaming Cafe");
  return doc.save();
}
