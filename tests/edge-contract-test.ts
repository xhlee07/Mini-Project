// Auth/storage/mail worker contract tests with an in-process fake backend. No external network or email.
let handler:(req:Request)=>Promise<Response>;
const originalServe=Deno.serve;
Deno.serve=((f:(req:Request)=>Promise<Response>)=>{handler=f;return {} as Deno.HttpServer}) as typeof Deno.serve;
for(const key of ['SMTP_USER','SMTP_APP_PASSWORD','RESEND_API_KEY','RECEIPT_FROM'])Deno.env.delete(key);
Deno.env.set('SUPABASE_URL','https://fixture.supabase.test');
Deno.env.set('SUPABASE_SECRET_KEYS',JSON.stringify({default:'sb_secret_fixture'}));
Deno.env.set('SUPABASE_PUBLISHABLE_KEYS',JSON.stringify({default:'sb_publishable_fixture'}));
let uploaded=false;let recipientArgument=false;
globalThis.fetch=async (input:RequestInfo|URL,init?:RequestInit)=>{
 const url=String(input);const headers=new Headers(init?.headers);
 if(url.endsWith('/auth/v1/user'))return Response.json(headers.get('Authorization')==='Bearer expired'?{}:{id:'00000000-0000-4000-8000-000000000001'},{status:headers.get('Authorization')==='Bearer expired'?401:200});
 if(url.endsWith('/ctfly_snapshot'))return Response.json({profile:{role:headers.get('Authorization')==='Bearer customer'?'customer':'staff',active:true}});
 if(url.includes('/storage/v1/object/')){if(headers.get('apikey')!=='sb_secret_fixture'||headers.has('Authorization'))throw Error('Storage service key handling incorrect');uploaded=true;return Response.json({});}
 if(url.endsWith('/ctfly_receipt_worker')){const b=JSON.parse(String(init?.body));if(b.p_secret!=='private-fixture-secret')return Response.json({message:'Worker authentication failed'},{status:400});recipientArgument=Boolean(b.p_data.email);return Response.json({configured:false});}
 throw Error('Unexpected outbound request');
};
await import('../supabase/functions/club-services/index.ts');
const call=(body:unknown,headers:Record<string,string>={})=>handler(new Request('https://fixture/functions',{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(body)}));
function check(value:boolean,label:string){if(!value)throw Error(label);}
check((await call({operation:'poster'})).status===401,'Unsigned request denied');
check((await call({operation:'poster'},{Authorization:'Bearer expired'})).status===401,'Expired login denied');
check((await call({operation:'poster',base64:'/9j/AA=='},{Authorization:'Bearer customer'})).status===403,'Customer upload denied');
check((await call({operation:'poster',base64:'bm90anBlZw=='},{Authorization:'Bearer staff'})).status===400,'Non JPEG denied');
const poster=await call({operation:'poster',base64:'/9j/AA=='},{Authorization:'Bearer staff'});check(poster.status===200&&uploaded,'Staff upload accepted');
const posterData=await poster.json();check(posterData.url.startsWith('https://fixture.supabase.test/storage/v1/object/public/event-posters/00000000-0000-4000-8000-000000000001/'),'Poster belongs to staff folder');
check((await call({operation:'dispatch'},{'X-Receipt-Worker':'wrong'})).status===403,'Wrong worker secret denied');
const mail=await call({operation:'dispatch',email:'somebody@example.com'},{'X-Receipt-Worker':'private-fixture-secret'});const state=await mail.json();check(mail.status===200&&state.configured===false&&state.processed===0&&!recipientArgument,'No mail before configuration and no caller-supplied recipient');
Deno.serve=originalServe;
console.log('PASS: 8 Edge contracts: unsigned/expired/customer access, image validation, staff Storage path, worker authentication and missing-mail configuration. No external network or email.');
