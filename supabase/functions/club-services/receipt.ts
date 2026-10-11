export type Bill = {id:string;kind:string;created_at:string;status:string;environment:string;method:string;payment_reference?:string;subtotal:number;discount:number;total:number;points:number;void_reason?:string;details?:Record<string,unknown>};
export const escapeHtml=(value:unknown)=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
export const money=(n:number)=>'RM '+(n/100).toFixed(2);
const date=(value:unknown)=>value?new Intl.DateTimeFormat('en-GB',{dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Kuala_Lumpur'}).format(new Date(String(value))):'';
export function receiptLines(b:Bill):string[] {
 const d=b.details??{};const lines=['CTFLY GAMING CAFE','Malaysia / MYR'];
 if(b.environment==='test') lines.push('TEST RECEIPT - NO MONEY CHARGED','School project payment simulation');
 lines.push('Receipt: '+b.id,'Issued: '+date(b.created_at),'Status: '+b.status,'Customer: '+String(d.customer_name??''),'Purchase: '+String(d.activity??b.kind));
 for(const [label,key] of [['Resource','resource'],['Zone','zone'],['Game','game'],['Team','team']]) if(d[key]) lines.push(label+': '+String(d[key]));
 if(d.starts_at)lines.push('Starts: '+date(d.starts_at));if(d.ends_at)lines.push('Ends: '+date(d.ends_at));
 if(d.duration_minutes)lines.push('Duration: '+d.duration_minutes+' minutes');if(d.players)lines.push('Players: '+d.players);
 if(d.hourly_rate)lines.push('Hourly rate: '+money(Number(d.hourly_rate)));
 lines.push('------------------------------------');
 for(const item of (d.items??[]) as {name:string;qty:number;price:number;line_total?:number}[])lines.push(`${item.qty} x ${item.name}   ${money(item.line_total??item.price)}`);
 lines.push('Subtotal: '+money(b.subtotal),'Discount: '+money(b.discount));if(d.promo_code)lines.push('Promo code: '+d.promo_code);
 lines.push('TOTAL: '+money(b.total),'Payment: '+b.method);if(b.payment_reference)lines.push('Reference: '+b.payment_reference);
 lines.push((b.environment==='test'?'Demo points: ':'Points earned: ')+b.points);
 if(b.status==='Void')lines.push('Void reason: '+(b.void_reason??''));
 lines.push('All session times: Malaysia (UTC+8)','Thank you for choosing CTFLY.');return lines;
}
export function receiptHtml(b:Bill):string {
 return '<!doctype html><html><body style="margin:0;padding:24px;background:#e9eeee"><main style="margin:auto;max-width:420px;background:white;color:#151919;padding:28px;border-radius:12px;font-family:monospace"><h1 style="font:700 22px sans-serif">CTFLY</h1>'+receiptLines(b).map(line=>'<div style="white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0">'+escapeHtml(line)+'</div>').join('')+'</main></body></html>';
}
