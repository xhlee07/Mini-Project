import nodemailer from 'npm:nodemailer@10.1.0';
import {Buffer} from 'node:buffer';
import {receiptHtml,receiptLines,type Bill} from './receipt.ts';
import {receiptPdf} from './pdf.ts';
const env=(name:string)=>Deno.env.get(name)??'';
const url=env('SUPABASE_URL');
function serverKey(name:string,fallback:string):string{try{return JSON.parse(env(name)).default||env(fallback);}catch{return env(fallback);}}
const admin=serverKey('SUPABASE_SECRET_KEYS','SUPABASE_SERVICE_ROLE_KEY');
const anon=serverKey('SUPABASE_PUBLISHABLE_KEYS','SUPABASE_ANON_KEY');
async function rpc(name:string,body:unknown,key:string,token=key.startsWith('eyJ')?key:''){const res=await fetch(url+'/rest/v1/rpc/'+name,{method:'POST',headers:{apikey:key,...(token?{Authorization:'Bearer '+token}:{}),'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});const data=await res.json();if(!res.ok)throw Error(data.message??'Request failed');return data;}
const json=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
async function dispatch(secret:string){
 const smtpUser=env('SMTP_USER'),smtpPassword=env('SMTP_APP_PASSWORD').replace(/\s/g,''),resend=env('RESEND_API_KEY'),from=env('RECEIPT_FROM');
 const useResend=Boolean(resend&&from);const configured=useResend||Boolean(smtpUser&&smtpPassword);
 const worker=(op:string,data:unknown={})=>rpc('ctfly_receipt_worker',{p_secret:secret,p_operation:op,p_data:data},admin);
 await worker('configure',{configured});if(!configured)return {configured:false,processed:0};
 const jobs=await worker('claim') as {bill:Bill;email:string}[];let processed=0;
 for(const job of jobs){
  let sending=false;
  try {
   if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(job.email))throw Error('Recipient unavailable');
   let pdf:Uint8Array|undefined;try{pdf=await receiptPdf(job.bill);}catch{/* A complete formal HTML receipt remains available if font/PDF generation fails. */}
   const subject=(job.bill.environment==='test'?'TEST ':'')+'CTFLY receipt '+job.bill.id.slice(0,8);
   const html=receiptHtml(job.bill),text=receiptLines(job.bill).join('\n');let providerId='';
   if(useResend){
    sending=true;
    const res=await fetch('https://api.resend.com/emails',{method:'POST',headers:{Authorization:'Bearer '+resend,'Content-Type':'application/json','Idempotency-Key':'ctfly-receipt-'+job.bill.id},body:JSON.stringify({from,to:[job.email],subject,html,text,...(pdf?{attachments:[{filename:'CTFLY-'+job.bill.id+'.pdf',content:Buffer.from(pdf).toString('base64')}]}:{})}),signal:AbortSignal.timeout(15000)});
    if(!res.ok){sending=false;throw Error('Mail provider rejected the request');}providerId=(await res.json()).id;
   }else{
    const port=Number(env('SMTP_PORT')||'465');
    const transport=nodemailer.createTransport({host:env('SMTP_HOST')||'smtp.gmail.com',port,secure:port===465,requireTLS:port!==465,auth:{user:smtpUser,pass:smtpPassword},connectionTimeout:10000,greetingTimeout:10000,socketTimeout:15000,disableFileAccess:true,disableUrlAccess:true});
    try{
     // Verify the connection before sending so configuration failures remain safe to retry.
     await transport.verify();sending=true;
     const result=await transport.sendMail({from:'CTFLY Gaming Cafe <'+smtpUser+'>',to:job.email,subject,html,text,messageId:'<ctfly-'+job.bill.id+'@'+smtpUser.split('@')[1]+'>',attachments:pdf?[{filename:'CTFLY-'+job.bill.id+'.pdf',content:Buffer.from(pdf),contentType:'application/pdf'}]:[]});
     if(!result.accepted?.length)throw Error('Recipient rejected');providerId=result.messageId;
    }finally{transport.close();}
   }
   await worker('result',{id:job.bill.id,status:'Sent',provider_id:providerId});processed++;
  }catch{
   await worker('result',{id:job.bill.id,status:sending?'Unknown':'Failed',error:sending?'Delivery was interrupted. Verify delivery before retrying.':'Mail configuration or recipient could not be verified.'});
  }
 }
 return {configured:true,processed};
}
Deno.serve(async req=>{
 if(req.method!=='POST')return json({message:'POST required'},405);
 try{
  const secret=req.headers.get('X-Receipt-Worker');
  if(secret){try{return json(await dispatch(secret));}catch(e){console.error('Receipt worker unavailable:',e instanceof Error?e.message:'Service failure');return json({message:'Worker authentication or service failed'},403);}}
  const bearer=req.headers.get('Authorization')??'';if(!bearer.startsWith('Bearer '))return json({message:'Sign in required'},401);
  const auth=await fetch(url+'/auth/v1/user',{headers:{apikey:anon,Authorization:bearer},signal:AbortSignal.timeout(10000)});if(!auth.ok)return json({message:'Sign in required'},401);
  const user=await auth.json();if(!user.id)return json({message:'Sign in required'},401);
  if(Number(req.headers.get('Content-Length')||0)>6000000)return json({message:'Image exceeds 4 MB'},413);
  const body=await req.json();if(body.operation!=='poster')return json({message:'Unsupported operation'},400);
  const snapshot=await rpc('ctfly_snapshot',{},anon,bearer.slice(7));
  if(!['staff','admin'].includes(snapshot.profile?.role)||!snapshot.profile?.active)return json({message:'Staff access required'},403);
  if(typeof body.base64!=='string'||body.base64.length>5592408)return json({message:'Image must be a JPEG under 4 MB'},400);
  const bytes=Buffer.from(body.base64,'base64');if(bytes.length>4194304||bytes.length<4||bytes[0]!==255||bytes[1]!==216||bytes[2]!==255)return json({message:'Choose a valid JPEG image'},400);
  const path=user.id+'/'+crypto.randomUUID()+'.jpg';const uploaded=await fetch(url+'/storage/v1/object/event-posters/'+path,{method:'POST',headers:{apikey:admin,...(admin.startsWith('eyJ')?{Authorization:'Bearer '+admin}:{}),'Content-Type':'image/jpeg','x-upsert':'false'},body:bytes,signal:AbortSignal.timeout(15000)});
  if(!uploaded.ok)return json({message:'Image could not be uploaded. Try again.'},502);
  return json({url:url+'/storage/v1/object/public/event-posters/'+path});
 }catch{return json({message:'Service could not complete the request. Try again.'},500);}
});
