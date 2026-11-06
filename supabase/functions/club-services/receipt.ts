export type Bill = {
  id: string;
  kind: string;
  created_at: string;
  status: string;
  environment: string;
  method: string;
  payment_reference?: string;
  subtotal: number;
  discount: number;
  total: number;
  points: number;
  void_reason?: string;
  details?: Record<string, unknown>;
};
export const escapeHtml = (value: unknown) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[c]!),
  );
export const money = (n: number) => "RM " + (n / 100).toFixed(2);
export const receiptPhone = "0173707898";
const formatDate = (value: unknown, options: Intl.DateTimeFormatOptions) => {
  if (!value) return "";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime())
    ? "Unavailable"
    : new Intl.DateTimeFormat("en-GB", {
      ...options,
      timeZone: "Asia/Kuala_Lumpur",
    }).format(parsed);
};
export const receiptDate = (value: unknown) =>
  formatDate(value, { dateStyle: "medium", timeStyle: "short", hour12: true });
export const receiptNumber = (b: Bill) => {
  const stamp = formatDate(b.created_at, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).split("/").reverse().join("");
  // Keep the full immutable bill ID: repeated downloads keep the same unique number.
  return "CT-" + stamp + "-" + b.id.toUpperCase();
};
export type ReceiptRow = { label: string; value: string };
export type ReceiptSection = { title: string; rows: ReceiptRow[] };
export function receiptSections(b: Bill): ReceiptSection[] {
  const d = b.details ?? {};
  const overview: ReceiptRow[] = [
    { label: "Receipt no.", value: receiptNumber(b) },
    { label: "Issued", value: receiptDate(b.created_at) },
    { label: "Status", value: b.status },
  ];
  if (d.customer_name) {
    overview.push({ label: "Customer", value: String(d.customer_name) });
  }
  const session: ReceiptRow[] = [{
    label: "Activity",
    value: String(d.activity ?? b.kind),
  }];
  for (
    const [label, key] of [["Space / item", "resource"], ["Zone", "zone"], [
      "Game",
      "game",
    ], ["Team", "team"]]
  ) if (d[key]) session.push({ label, value: String(d[key]) });
  if (d.starts_at) {
    session.push({ label: "Start", value: receiptDate(d.starts_at) });
  }
  if (d.ends_at) session.push({ label: "End", value: receiptDate(d.ends_at) });
  if (Number(d.duration_minutes) > 0) {
    session.push({ label: "Duration", value: d.duration_minutes + " minutes" });
  }
  if (Number(d.players) > 0) {
    session.push({ label: "Players", value: String(d.players) });
  }
  if (d.hourly_rate !== undefined && d.hourly_rate !== null) {
    session.push({
      label: "Hourly rate",
      value: money(Number(d.hourly_rate)) + " / hour",
    });
  }
  const payment: ReceiptRow[] = [{ label: "Method", value: b.method }];
  if (b.payment_reference) {
    payment.push({ label: "Reference", value: b.payment_reference });
  }
  payment.push({
    label: b.status === "Void"
      ? "Points reversed"
      : b.environment === "test"
      ? "Demo points"
      : "Points earned",
    value: String(b.points),
  });
  if (b.status === "Void") {
    payment.push({
      label: "Void reason",
      value: b.void_reason || "Contact CTFLY for details.",
    });
  }
  return [{ title: "Receipt details", rows: overview }, {
    title: b.kind === "Order" ? "Order details" : "Session details",
    rows: session,
  }, { title: "Payment details", rows: payment }];
}
export const receiptItems = (b: Bill) =>
  ((b.details?.items ?? []) as {
    name: string;
    qty: number;
    price: number;
    line_total?: number;
  }[]).map((item) => ({
    label: `${item.qty} x ${item.name}`,
    value: money(item.line_total ?? item.qty * item.price),
  }));
export function receiptLines(b: Bill): string[] {
  const sections = receiptSections(b);
  const lines = ["CTFLY GAMING CAFE", "Malaysia / MYR", "Tel: " + receiptPhone];
  if (b.environment === "test") {
    lines.push(
      "TEST RECEIPT - NO MONEY CHARGED",
      "School project payment simulation",
    );
  }
  for (const section of sections.slice(0, 2)) {
    lines.push(
      "------------------------------------",
      section.title.toUpperCase(),
    );
    for (const row of section.rows) lines.push(row.label + ": " + row.value);
  }
  lines.push("------------------------------------", "CHARGES");
  for (const row of receiptItems(b)) lines.push(row.label + "   " + row.value);
  lines.push(
    "Subtotal: " + money(b.subtotal),
    "Discount: " + money(b.discount),
  );
  if (b.details?.promo_code) lines.push("Promo code: " + b.details.promo_code);
  lines.push(
    "TOTAL: " + money(b.total),
    "------------------------------------",
    "PAYMENT DETAILS",
  );
  for (const row of sections[2].rows) lines.push(row.label + ": " + row.value);
  lines.push(
    "------------------------------------",
    "All dates and times: Malaysia (UTC+8)",
    "Keep this receipt for your booking.",
    "Thank you for choosing CTFLY.",
    "We hope to see you again!",
  );
  return lines;
}
/** Inline tables keep the layout readable in Gmail, including narrow phone screens. */
export function receiptHtml(b: Bill, logoSrc = "cid:ctfly-logo"): string {
  const sections = receiptSections(b);
  const title = b.kind === "Order"
    ? "Order receipt"
    : b.kind === "Event"
    ? "Event receipt"
    : "Booking receipt";
  const row = (label: unknown, value: unknown) =>
    `<tr><td style="padding:5px 10px 5px 0;width:31%;font-size:12px;color:#6d7b81;vertical-align:top">${
      escapeHtml(label)
    }</td><td style="padding:5px 0;font-size:12px;color:#172127;vertical-align:top;overflow-wrap:anywhere;word-break:break-word">${
      escapeHtml(value)
    }</td></tr>`;
  const heading = (label: string) =>
    `<div style="font-size:10px;font-weight:700;letter-spacing:1.6px;color:#6d7b81;margin:25px 0 12px">${
      escapeHtml(label.toUpperCase())
    }</div>`;
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
  const fieldValue = (r: ReceiptRow) => {
    const parts = ["Start", "End"].includes(r.label) ? r.value.split(", ") : [];
    return parts.length === 2
      ? escapeHtml(parts[0]) + '<br><span style="white-space:nowrap">' +
        escapeHtml(parts[1]) + "</span>"
      : escapeHtml(r.value);
  };
  let grid = "";
  for (let i = 0; i < fields.length; i += 2) {
    grid += "<tr>" + fields.slice(i, i + 2).map((r) =>
      `<td width="50%" style="padding:12px;vertical-align:top"><div style="font-size:10px;color:#6d7b81;margin-bottom:5px">${
        escapeHtml(r.label.toUpperCase())
      }</div><div style="font-size:13px;font-weight:600;color:#172127;overflow-wrap:anywhere;word-break:break-word">${
        fieldValue(r)
      }</div></td>`
    ).join("") + (fields.length === i + 1 ? '<td width="50%"></td>' : "") +
      "</tr>";
  }
  const amount = (label: unknown, value: unknown) =>
    `<tr><td style="padding:7px 8px 7px 0;font-size:13px;vertical-align:top;overflow-wrap:anywhere">${
      escapeHtml(label)
    }</td><td style="padding:7px 0;font-size:13px;font-weight:600;text-align:right;vertical-align:top;white-space:nowrap">${
      escapeHtml(value)
    }</td></tr>`;
  const items = receiptItems(b);
  const charges = items.length ? items : [{
    label: String(b.details?.activity ?? b.kind),
    value: money(b.subtotal),
  }];
  return `<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CTFLY receipt</title></head><body style="margin:0;padding:24px 10px;background:#eef1f2;color:#172127;font-family:Arial,Helvetica,sans-serif;line-height:1.45"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:600px;background:#fff;border-radius:12px;overflow:hidden"><tr><td style="background:#141d22;padding:22px 20px"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td width="58" style="vertical-align:middle"><img src="${
    escapeHtml(logoSrc)
  }" alt="CTFLY app logo" width="48" height="48" style="display:block;border:0;border-radius:8px"></td><td style="vertical-align:middle"><div style="color:#fff;font-size:23px;font-weight:800;letter-spacing:1.6px">CTFLY</div><div style="color:#b5c2c7;font-size:9px;letter-spacing:.5px">GAMING CAFE / MALAYSIA</div></td><td style="color:#fff;font-size:11px;text-align:right;white-space:nowrap;padding-left:6px"><a href="tel:+60173707898" style="color:#fff;text-decoration:none">${receiptPhone}</a></td></tr></table></td></tr><tr><td style="padding:26px 24px"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td style="vertical-align:top;font-size:26px;font-weight:700;padding-right:8px">${title}</td><td style="vertical-align:top;text-align:right"><span style="display:inline-block;background:${
    b.status === "Void" ? "#ffe8db" : "#c6f56b"
  };padding:7px 10px;font-size:10px;font-weight:bold;white-space:nowrap">${
    escapeHtml(b.status.toUpperCase())
  }</span></td></tr></table>${
    b.environment === "test"
      ? '<div style="background:#fff5de;color:#705321;font-size:11px;padding:10px 12px;margin:18px 0 12px"><strong>TEST RECEIPT - NO MONEY CHARGED</strong></div>'
      : ""
  }<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="table-layout:fixed;margin-top:14px">${
    sections[0].rows.filter((r) => r.label !== "Status").map((r) =>
      row(r.label, r.value)
    ).join("")
  }</table>${
    heading(sections[1].title)
  }<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="table-layout:fixed;background:#f3f5f6;border-radius:8px">${grid}</table>${
    heading("Charges")
  }<table role="presentation" width="100%" cellpadding="0" cellspacing="0">${
    charges.map((r) => amount(r.label, r.value)).join("")
  }<tr><td colspan="2" style="height:8px;border-bottom:1px solid #dfe5e8"></td></tr>${
    amount("Subtotal", money(b.subtotal))
  }${
    amount(
      "Discount" + (b.details?.promo_code ? " / " + b.details.promo_code : ""),
      b.discount ? "- " + money(b.discount) : money(0),
    )
  }</table><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#141d22;margin-top:14px;border-radius:8px"><tr><td style="padding:17px 14px;color:#fff;font-size:11px;font-weight:700">${
    b.status === "Void"
      ? "VOIDED TOTAL"
      : b.environment === "test"
      ? "TEST TOTAL"
      : "TOTAL PAID"
  }</td><td style="padding:14px;color:#c6f56b;text-align:right;font-size:28px;font-weight:700;white-space:nowrap">${
    money(b.total)
  }</td></tr></table>${
    heading(sections[2].title)
  }<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="table-layout:fixed">${
    sections[2].rows.map((r) => row(r.label, r.value)).join("")
  }</table><div style="margin-top:22px;border-top:1px solid #dfe5e8;padding-top:20px"><div style="font-size:13px;font-weight:700">Thanks for playing with us.</div><div style="font-size:11px;color:#6d7b81;margin-top:7px">Your detailed PDF receipt is attached.<br>All dates and times: Malaysia (UTC+8) / MYR.<br>Keep this receipt for your booking.</div></div></td></tr></table></td></tr></table></body></html>`;
}
