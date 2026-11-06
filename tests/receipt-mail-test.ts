import {
  type Bill,
  receiptHtml,
  receiptItems,
  receiptLines,
  receiptNumber,
} from "../supabase/functions/club-services/receipt.ts";
import { receiptPdf } from "../supabase/functions/club-services/pdf.ts";
import { logoBase64 } from "../supabase/functions/club-services/brand.ts";
import { PDFDocument } from "npm:pdf-lib@1.17.1";
const bill: Bill = {
  id: "demo-receipt-2026-001",
  kind: "Booking",
  created_at: "2026-10-11T02:35:00Z",
  status: "Paid",
  environment: "test",
  method: "Test TNG",
  payment_reference: "TEST-DEMO-RECEIPT",
  subtotal: 1200,
  discount: 300,
  total: 900,
  points: 9,
  details: {
    customer_name: "Student 李明",
    activity: "Racing simulator",
    resource: "VIP Racing Simulator 01",
    zone: "VIP",
    starts_at: "2026-10-12T04:00:00Z",
    ends_at: "2026-10-12T05:30:00Z",
    duration_minutes: 90,
    players: 1,
    hourly_rate: 800,
    promo_code: "SCHOOL25",
  },
};
const lines = receiptLines(bill);
if (
  !lines.includes("Duration: 90 minutes") ||
  !lines.includes("TOTAL: RM 9.00") || !lines.some((l) => l.includes("12 Oct"))
) throw Error("Receipt details missing");
const unsafe = {
  ...bill,
  details: {
    ...bill.details,
    customer_name: '<script>alert("unsafe")</script>',
  },
};
if (
  receiptHtml(unsafe).includes("<script>") ||
  !receiptHtml(unsafe).includes("&lt;script&gt;")
) throw Error("HTML receipt escaping failed");
const html = receiptHtml(bill);
for (
  const value of [
    "0173707898",
    "cid:ctfly-logo",
    "12 Oct 2026",
    "12:00",
    "1:30",
    "RM 12.00",
    "RM 3.00",
    "RM 9.00",
    "SCHOOL25",
    "TEST RECEIPT - NO MONEY CHARGED",
  ]
) {
  if (!html.includes(value)) {
    throw Error("Missing full receipt field: " + value);
  }
}
if (
  receiptNumber(bill) !== receiptNumber({ ...bill }) ||
  receiptNumber(bill) === receiptNumber({ ...bill, id: bill.id + "2" })
) throw Error("Receipt numbers must be stable and unique");
const order = {
  ...bill,
  kind: "Order",
  subtotal: 2300,
  discount: 0,
  total: 2300,
  points: 23,
  details: {
    activity: "Cafe order",
    customer_name: "Student 李明",
    items: [{ name: "Coffee", qty: 3, price: 650 }, {
      name: "Water",
      qty: 2,
      price: 175,
      line_total: 350,
    }],
  },
};
if (
  receiptItems(order)[0].value !== "RM 19.50" ||
  receiptItems(order)[1].value !== "RM 3.50"
) throw Error("Order quantity or frozen line total incorrect");
const frozenOrder = {...order,details:{...order.details,items:[{name:'Water',qty:2,price:999,line_total:350}]}};
if(receiptItems(frozenOrder)[0].value!=='RM 3.50')throw Error('Frozen line total must take precedence over unit price');
if (
  receiptHtml(order).includes("Session details") ||
  receiptHtml(order).includes("Duration")
) throw Error("Order must not invent session details");
const overnight = {
  ...bill,
  details: {
    ...bill.details,
    starts_at: "2026-10-14T15:30:00Z",
    ends_at: "2026-10-14T17:00:00Z",
  },
};
if (
  !receiptHtml(overnight).includes("14 Oct 2026") ||
  !receiptHtml(overnight).includes("15 Oct 2026")
) throw Error("Overnight dates incorrect");
if (
  !receiptHtml({ ...bill, status: "Void", void_reason: "Refunded" }).includes(
    "VOIDED TOTAL",
  )
) throw Error("Voided receipt is misleading");
await Deno.mkdir("build/test-results/receipt-mail", { recursive: true });
const bytes = await receiptPdf(bill);
if (bytes.byteLength < 1000) throw Error("PDF missing");
const document=await PDFDocument.load(bytes);
if(document.getPageCount()!==1||document.getPage(0).getWidth()!==595||document.getPage(0).getHeight()!==842)throw Error('Booking receipt must use a single A4 page');
const longOrder={...order,subtotal:7000,total:7000,points:70,details:{...order.details,items:Array.from({length:28},(_,i)=>({name:'Energy drink '+(i+1),qty:1,price:250}))}};
const longBytes=await receiptPdf(longOrder);
if((await PDFDocument.load(longBytes)).getPageCount()<2)throw Error('Long order must paginate instead of clipping');
await Deno.writeFile('build/test-results/receipt-mail/receipt-long-order.pdf',longBytes);
await Deno.writeFile(
  "build/test-results/receipt-mail/receipt-email.pdf",
  bytes,
);
await Deno.writeTextFile(
  "build/test-results/receipt-mail/receipt-email.html",
  receiptHtml(bill, "data:image/jpeg;base64," + logoBase64),
);
await Deno.writeTextFile(
  "build/test-results/receipt-mail/receipt-order.html",
  receiptHtml(order, "data:image/jpeg;base64," + logoBase64),
);
await Deno.writeFile(
  "build/test-results/receipt-mail/receipt-order.pdf",
  await receiptPdf(order),
);
console.log(
  "PASS: formal email receipt, HTML escaping, all session fields and Unicode PDF generation. No email was sent.",
);
