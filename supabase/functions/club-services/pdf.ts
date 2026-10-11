import {PDFDocument,StandardFonts,rgb} from 'npm:pdf-lib@1.17.1';
import fontkit from 'npm:@pdf-lib/fontkit@1.1.1';
import {receiptLines,type Bill} from './receipt.ts';
let fontBytes:Uint8Array|undefined;
export async function receiptPdf(b:Bill):Promise<Uint8Array> {
 const doc=await PDFDocument.create();const lines=receiptLines(b);const latin=await doc.embedFont(StandardFonts.Helvetica);let unicode=latin;
 if(lines.some(line=>/[^\x20-\x7e]/.test(line))) {
  doc.registerFontkit(fontkit);
  if(!fontBytes){const res=await fetch('https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf',{signal:AbortSignal.timeout(15000)});if(!res.ok)throw Error('Font unavailable');fontBytes=new Uint8Array(await res.arrayBuffer());}
  unicode=await doc.embedFont(fontBytes,{subset:false});
 }
 const fontFor=(char:string)=>/[^\x20-\x7e]/.test(char)?unicode:latin;
 const width=(text:string)=>Array.from(text).reduce((sum,char)=>sum+fontFor(char).widthOfTextAtSize(char,10),0);
 const wrapped:string[]=[];
 for(const line of lines){let part='';for(const char of line){if(width(part+char)>276){wrapped.push(part);part='';}part+=char;}wrapped.push(part);}
 for(let offset=0;offset<wrapped.length;offset+=46){const section=wrapped.slice(offset,offset+46);const h=Math.max(240,72+section.length*15);const page=doc.addPage([320,h]);section.forEach((line,i)=>{let x=22;for(const char of line){const font=fontFor(char);page.drawText(char,{x,y:h-32-i*15,size:10,font,color:rgb(.06,.08,.08)});x+=font.widthOfTextAtSize(char,10);}});page.drawText('Page '+(offset/46+1),{x:22,y:20,size:8,font:latin});}
 doc.setTitle('CTFLY receipt '+b.id);doc.setAuthor('CTFLY Gaming Cafe');return doc.save();
}
