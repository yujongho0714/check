// ===== 마이룸: 복셀 가구 + 마크 캐릭터 + 바람(옷 입히기) 캐릭터. 출결 앱(AB 다리)과 연결 =====
'use strict';
const $=id=>document.getElementById(id);
const AB=window.AB||null;   // 안드로이드 다리 (없으면 미리보기용 가짜 자료)
const toast=t=>{const e=$('toast');e.textContent=t;e.classList.add('on');clearTimeout(e._t);e._t=setTimeout(()=>e.classList.remove('on'),1700)};
const rnd2=(a,b)=>a+Math.random()*(b-a);
// ---------- 상태 (앱에서 받음) ----------
let ST={name:'',gold:0,mc:[],mcSkin:'',room:{fur:[],placed:[],npc:[],ctl:''},dress:null,admin:false,custom:[],furPrice:1,mcPrice:10,mcSell:8};
const RSELL={'노말':1,'레어':1,'에픽':2,'유니크':3,'레전드리':5};
const RCOL={'노말':'#8a8fa3','레어':'#3a7bd5','에픽':'#9b59e6','유니크':'#c99a00','레전드리':'#23a35a'};
let FUR={},SKINS={},CLIPS=[];   // 가구 정의 · 캐릭터 스킨(키 → 그림 주소/이름) · 동작
// ---------- 장면 ----------
const st=$('stage');const R=new THREE.WebGLRenderer({antialias:true,alpha:true});R.setPixelRatio(Math.min(2,devicePixelRatio||1));R.shadowMap.enabled=true;st.appendChild(R.domElement);
const S=new THREE.Scene();const N=8;
const cam=new THREE.OrthographicCamera(-6,6,4.5,-4.5,.1,100);cam.position.set(10,10.5,10);cam.lookAt(0,1.2,0);
S.add(new THREE.HemisphereLight(0xffffff,0xbfa98a,.75));const sun=new THREE.DirectionalLight(0xfff3e0,.75);sun.position.set(5,10,3);sun.castShadow=true;sun.shadow.mapSize.set(1024,1024);Object.assign(sun.shadow.camera,{left:-7,right:7,top:7,bottom:-7});S.add(sun);
function pixTex(base,v,sz,pattern){const c=document.createElement('canvas');c.width=c.height=sz;const g=c.getContext('2d');const b=new THREE.Color(base);
 for(let y=0;y<sz;y++)for(let x=0;x<sz;x++){let k=1+(Math.random()-.5)*v;if(pattern)k*=pattern(x,y);const cc=b.clone().multiplyScalar(k);cc.r=Math.min(1,cc.r);cc.g=Math.min(1,cc.g);cc.b=Math.min(1,cc.b);g.fillStyle='#'+cc.getHexString();g.fillRect(x,y,1,1)}
 const t=new THREE.CanvasTexture(c);t.magFilter=t.minFilter=THREE.NearestFilter;t.wrapS=t.wrapT=THREE.RepeatWrapping;return t}
const fl=pixTex(0xd9a46a,.12,16,(x,y)=>(y%4===0?.82:1)*((x+(y>>2)*5)%16===0?.85:1));fl.repeat.set(N/2,N/2);
const floor=new THREE.Mesh(new THREE.BoxGeometry(N,.3,N),new THREE.MeshLambertMaterial({map:fl}));floor.position.y=-.15;floor.receiveShadow=true;S.add(floor);
const wp=pixTex(0xa9d4f5,.05,16,(x)=>(x%8<1?.92:1));wp.repeat.set(2,1);const wm=new THREE.MeshLambertMaterial({map:wp});
const w1=new THREE.Mesh(new THREE.BoxGeometry(N,3.4,.3),wm);w1.position.set(0,1.7,-N/2-.15);w1.receiveShadow=true;S.add(w1);
const w2=new THREE.Mesh(new THREE.BoxGeometry(.3,3.4,N),wm);w2.position.set(-N/2-.15,1.7,0);w2.receiveShadow=true;S.add(w2);
const wood=new THREE.MeshLambertMaterial({map:pixTex(0x8a5a2b,.15,8)});[[0,.15,-N/2+.02,N,.3,.06],[-N/2+.02,.15,0,.06,.3,N]].forEach(([x,y,z,a,b,c])=>{const m=new THREE.Mesh(new THREE.BoxGeometry(a,b,c),wood);m.position.set(x,y,z);S.add(m)});
const glass=new THREE.Mesh(new THREE.BoxGeometry(1.8,1.4,.06),new THREE.MeshBasicMaterial({color:0xdff4ff}));glass.position.set(1.2,2.2,-N/2+.02);S.add(glass);
const white=new THREE.MeshLambertMaterial({color:0xffffff});[[1.2,2.95,2,.12],[1.2,1.45,2,.12],[.25,2.2,.12,1.6],[2.15,2.2,.12,1.6],[1.2,2.2,.08,1.5]].forEach(([x,y,a,b])=>{const m=new THREE.Mesh(new THREE.BoxGeometry(a,b,.12),white);m.position.set(x,y,-N/2+.05);S.add(m)});
// ---------- 복셀 가구 (.mbin) ----------
function mbGeo(b64){const u=Uint8Array.from(atob(b64),c=>c.charCodeAt(0));const dv=new DataView(u.buffer);const n=dv.getUint32(0,true);const mn=[0,1,2].map(i=>dv.getFloat32(4+i*4,true)),rg=[0,1,2].map(i=>dv.getFloat32(16+i*4,true));
 const q=new Int16Array(u.buffer.slice(28,28+n*6));const c=u.subarray(28+n*6,28+n*9);const pos=new Float32Array(n*3),col=new Float32Array(n*3);
 for(let i=0;i<n*3;i++){const a=i%3;pos[i]=mn[a]+(q[i]+32768)/65535*rg[a];col[i]=Math.pow(c[i]/255,2.2)}
 const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.BufferAttribute(pos,3));g.setAttribute('color',new THREE.BufferAttribute(col,3));g.computeVertexNormals();g.computeBoundingBox();return g}
const VMAT=new THREE.MeshLambertMaterial({vertexColors:true});const GEO={};
// ---------- PC에서 올린 3D 피규어(.geo.json)·그림 카드 : 큰 데이터는 필요할 때 서버에서 받아요 ----------
function bedrock(json,img){const j=typeof json==='string'?JSON.parse(json):json;let geo=null,tw=64,th=64;
  if(j['minecraft:geometry']){geo=j['minecraft:geometry'][0];tw=geo.description&&geo.description.texture_width||64;th=geo.description&&geo.description.texture_height||64}
  else{const k=Object.keys(j).find(k=>k.startsWith('geometry'));geo=j[k];tw=geo&&geo.texturewidth||64;th=geo&&geo.textureheight||64}
  if(!geo||!geo.bones)throw new Error('뼈대(bones)가 없어요');
  let tex=null;if(img){tex=new THREE.CanvasTexture(img);tex.magFilter=THREE.NearestFilter;tex.minFilter=THREE.NearestFilter;tex.encoding=THREE.sRGBEncoding}
  const mat=new THREE.MeshLambertMaterial({map:tex,color:tex?0xffffff:0xd0a070,alphaTest:.4,side:THREE.DoubleSide});const root=new THREE.Group(),G={},D=Math.PI/180;
  const faceUV=(gm,fi,u,v,w,h,fx)=>{const uv=gm.attributes.uv;const u0=u/tw,u1=(u+w)/tw,v0=1-v/th,v1=1-(v+h)/th;const a=fx?[u1,u0]:[u0,u1];uv.setXY(fi*4,a[0],v0);uv.setXY(fi*4+1,a[1],v0);uv.setXY(fi*4+2,a[0],v1);uv.setXY(fi*4+3,a[1],v1)};
  geo.bones.forEach(b=>{const g=new THREE.Group();g.name=b.name;const pv=b.pivot||[0,0,0];g.userData.pv=pv;G[b.name]=g;
    (b.cubes||[]).forEach(c=>{const [w,h,d]=c.size||[1,1,1],o=c.origin||[0,0,0],inf=c.inflate||0;const gm=new THREE.BoxGeometry(Math.max(w+inf*2,.001)/16,Math.max(h+inf*2,.001)/16,Math.max(d+inf*2,.001)/16);
      if(Array.isArray(c.uv)){const [u,v]=c.uv;const W=Math.floor(w),H=Math.floor(h),Dd=Math.floor(d);faceUV(gm,0,u+Dd+W,v+Dd,Dd,H,c.mirror);faceUV(gm,1,u,v+Dd,Dd,H,c.mirror);faceUV(gm,2,u+Dd,v,W,Dd);faceUV(gm,3,u+Dd+W,v,W,Dd);faceUV(gm,4,u+Dd+W+Dd,v+Dd,W,H,c.mirror);faceUV(gm,5,u+Dd,v+Dd,W,H,c.mirror)}
      else if(c.uv){const F=c.uv;['west','east','up','down','south','north'].forEach((n,i)=>{const f=F[n];if(f&&f.uv){const s=f.uv_size||[0,0];faceUV(gm,i,Math.min(f.uv[0],f.uv[0]+s[0]),Math.min(f.uv[1],f.uv[1]+s[1]),Math.abs(s[0]),Math.abs(s[1]),s[0]<0)}})}
      const m=new THREE.Mesh(gm,mat);m.castShadow=true;const cx=-(o[0]+w/2),cy=o[1]+h/2,cz=o[2]+d/2;
      if(c.rotation){const cp=c.pivot||[0,0,0];const sub=new THREE.Group();sub.position.set(-cp[0]/16,cp[1]/16,cp[2]/16);sub.rotation.set(-c.rotation[0]*D,c.rotation[1]*D,-c.rotation[2]*D,'ZYX');m.position.set((cx+cp[0])/16,(cy-cp[1])/16,(cz-cp[2])/16);sub.add(m);sub.userData.abs=1;g.add(sub)}
      else{m.position.set(cx/16,cy/16,cz/16);m.userData.abs=1;g.add(m)}});
    if(b.rotation)g.userData.rot=b.rotation});
  geo.bones.forEach(b=>{const g=G[b.name],pv=g.userData.pv;const par=b.parent&&G[b.parent];const ppv=par?par.userData.pv:[0,0,0];
    g.position.set(-(pv[0]-ppv[0])/16,(pv[1]-ppv[1])/16,(pv[2]-ppv[2])/16);g.children.forEach(ch=>{if(ch.userData.abs)ch.position.sub(new THREE.Vector3(-pv[0]/16,pv[1]/16,pv[2]/16))});
    if(g.userData.rot){const r=g.userData.rot;g.rotation.set(-r[0]*D,r[1]*D,-r[2]*D,'ZYX')}(par||root).add(g)});
  return root}

const ASSET={},AWAIT={};
function needAsset(k){if(ASSET[k]||AWAIT[k])return;AWAIT[k]=[];try{AB&&AB.assetData(k)}catch(e){}}
function gotAsset(r){const k=r.k;if(!r.ok){delete AWAIT[k];return}const img=new Image();img.onload=()=>{ASSET[k]={g:r.g,img};const L=AWAIT[k]||[];delete AWAIT[k];L.forEach(fill);delete THUMB[k];try{rBag()}catch(e){}};img.src=r.b}
function fill(o){const k=o.userData.ak,a=ASSET[k],f=FUR[k];if(!a||!f)return;let m;try{m=f.fmt==='bed'?bedrock(a.g,a.img):(()=>{const t=new THREE.CanvasTexture(a.img);t.magFilter=THREE.NearestFilter;const ar=a.img.width/a.img.height;const pl=new THREE.Mesh(new THREE.PlaneGeometry(ar,1),new THREE.MeshLambertMaterial({map:t,alphaTest:.4,side:THREE.DoubleSide}));pl.position.y=.5;pl.rotation.y=Math.PI/4;const g=new THREE.Group();g.add(pl);return g})()}catch(e){return}
  // 크기 · 바닥 맞추기 (한 칸 크기 피규어)
  const b=new THREE.Box3().setFromObject(m),sz=b.getSize(new THREE.Vector3());const s=Math.min(1.25/Math.max(sz.y,1e-4),1.1/Math.max(sz.x,sz.z,1e-4));m.scale.multiplyScalar(s);const b2=new THREE.Box3().setFromObject(m),c=b2.getCenter(new THREE.Vector3());m.position.set(f.w/2-c.x,-b2.min.y,f.d/2-c.z);
  m.traverse(x=>{if(x.isMesh){x.castShadow=true;x.receiveShadow=true}});while(o.children.length)o.remove(o.children[0]);o.add(m)}
function defFur(f){if(f.fmt==='bed'||f.fmt==='spr'){FUR[f.k]={n:f.n,r:f.r||'노말',w:1,d:1,custom:1,fmt:f.fmt,mk:()=>{const o=new THREE.Group();o.userData.ak=f.k;if(ASSET[f.k])fill(o);else{needAsset(f.k);(AWAIT[f.k]||[]).push(o);const ph=new THREE.Mesh(new THREE.BoxGeometry(.5,.5,.5),new THREE.MeshLambertMaterial({color:0xffe08a,transparent:true,opacity:.6}));ph.position.set(.5,.25,.5);o.add(ph)}return o}};return}
  FUR[f.k]={n:f.n,r:f.r,w:f.w,d:f.d,custom:!!f.custom,mk:()=>{const g=GEO[f.k]||(GEO[f.k]=mbGeo(f.b));const m=new THREE.Mesh(g,VMAT);m.castShadow=m.receiveShadow=true;const bb=g.boundingBox;const s=1/1.6;m.scale.setScalar(s);
  m.position.set(-(bb.min.x+bb.max.x)/2*s+f.w/2,-bb.min.y*s,-(bb.min.z+bb.max.z)/2*s+f.d/2);const o=new THREE.Group();o.add(m);return o}}}
// 썸네일
const TR=new THREE.WebGLRenderer({antialias:true,alpha:true,preserveDrawingBuffer:true});TR.setSize(128,128);const TS=new THREE.Scene();TS.add(new THREE.HemisphereLight(0xffffff,0xbfa98a,.9));const tl=new THREE.DirectionalLight(0xffffff,.6);tl.position.set(4,8,3);TS.add(tl);
const TC=new THREE.OrthographicCamera(-1.8,1.8,1.8,-1.8,.1,50);TC.position.set(6,6,6);TC.lookAt(0,.8,0);const THUMB={};
function thumb(k){if(THUMB[k])return THUMB[k];const f=FUR[k];if(!f)return '';const o=f.mk();o.position.set(-f.w/2,0,-f.d/2);TS.add(o);const s=Math.max(f.w,f.d)/2.2;TC.left=TC.bottom=-1.8*s;TC.right=TC.top=1.8*s;TC.updateProjectionMatrix();TR.render(TS,TC);THUMB[k]=TR.domElement.toDataURL();TS.remove(o);return THUMB[k]}
// ---------- 마크 캐릭터 ----------
const TXC={};function skinTex(k){if(TXC[k])return TXC[k];const s=SKINS[k]||SKINS['000'];const t=new THREE.TextureLoader().load(s?s.u:'../mc/000.png');t.magFilter=t.minFilter=THREE.NearestFilter;return TXC[k]=t}
function mcBox(w,h,d,u,v,inf){const s=1/16,e=(inf||0)*s,g=new THREE.BoxGeometry(w*s+e*2,h*s+e*2,d*s+e*2),uv=g.attributes.uv;const Rr=(x,y,ww,hh)=>{const u0=x/64,u1=(x+ww)/64,vt=1-y/64,vb=1-(y+hh)/64;return[[u0,vt],[u1,vt],[u0,vb],[u1,vb]]};
 const F=[Rr(u+d+w,v+d,d,h),Rr(u,v+d,d,h),Rr(u+d,v,w,d),Rr(u+d+w,v,w,d),Rr(u+d,v+d,w,h),Rr(u+2*d+w,v+d,w,h)];for(let f=0;f<6;f++)for(let k=0;k<4;k++)uv.setXY(f*4+k,F[f][k][0],F[f][k][1]);return g}
function mkChar(k){const M1=new THREE.MeshLambertMaterial({map:skinTex(k),alphaTest:.5}),M2=new THREE.MeshLambertMaterial({map:skinTex(k),alphaTest:.5,side:THREE.DoubleSide});const C={root:new THREE.Group(),mats:[M1,M2],tips:[]};
 // 뼈대: 골반(엉덩이 높이) → 다리 · 가슴 → 팔 · 머리. 동작은 골반을 중심으로 돌아서 공중돌기·옆돌기가 자연스러워요
 const hips=new THREE.Group();hips.position.y=12/16;C.root.add(hips);C.body=hips;const chest=new THREE.Group();hips.add(chest);C.chest=chest;
 const part=(par,w,h,d,u,v,u2,v2,px,py,pz,oy,tip)=>{const g=new THREE.Group();g.position.set(px/16,py/16,pz/16);const m=new THREE.Mesh(mcBox(w,h,d,u,v),M1);m.position.y=oy/16;m.castShadow=true;g.add(m);const m2=new THREE.Mesh(mcBox(w,h,d,u2,v2,.5),M2);m2.position.y=oy/16;g.add(m2);par.add(g);
  if(tip!=null){const t=new THREE.Object3D();t.position.y=tip/16;g.add(t);C.tips.push(t)}return g};
 C.legR=part(hips,4,12,4,0,16,0,32,-2,0,0,-6,-12);C.legL=part(hips,4,12,4,16,48,0,48,2,0,0,-6,-12);part(chest,8,12,4,16,16,16,32,0,0,0,6);
 C.armR=part(chest,4,12,4,40,16,40,32,-6,10,0,-4,-10);C.armL=part(chest,4,12,4,32,48,48,48,6,10,0,-4,-10);C.head=part(chest,8,8,8,0,0,32,0,0,12,0,4,8);return C}
// ---------- 동작 (선생님이 메이커에 넣은 모캡 동작을 관절 회전으로 미리 바꿔 둔 것) ----------
const PZ0={legR:[0,0],legL:[0,0],armR:[0,0],armL:[0,0],lean:0,roll:0};
const pz=p=>Object.assign({},PZ0,JSON.parse(JSON.stringify(p||{})));
const L2=(a,b,t)=>a+(b-a)*t;const SC=.75/.825;   // 사람 엉덩이 높이(0.825m) → 마크 엉덩이 높이
const _qa=new THREE.Quaternion(),_qb=new THREE.Quaternion(),_v=new THREE.Vector3(),_e=new THREE.Euler();
function clipAt(sk,t){const n=sk.fr.length,x=Math.max(0,Math.min(n-1,t*sk.fps)),i=Math.floor(x),j=Math.min(n-1,i+1),k=x-i;return{A:sk.fr[i],B:sk.fr[j],k,done:t*sk.fps>=n-1}}
function clipRoot(f){const A=f.A,B=f.B,k=f.k;return[L2(A[28],B[28],k)*SC,L2(A[29],B[29],k)*SC,L2(A[30],B[30],k)*SC]}
function clipYaw(f){_qa.fromArray(f.A,0);_qb.fromArray(f.B,0);_qa.slerp(_qb,f.k);_e.setFromQuaternion(_qa,'YXZ');return _e.y}
function applyClip(c,f){const J=[c.body,c.chest,c.head,c.armR,c.armL,c.legR,c.legL];J.forEach((o,m)=>{_qa.fromArray(f.A,m*4);_qb.fromArray(f.B,m*4);o.quaternion.copy(_qa).slerp(_qb,f.k)});
 const r=clipRoot(f);c.body.position.set(r[0],r[1],r[2]);
 // 손·발·머리가 바닥 밑으로 들어가지 않게 올려 줌
 c.root.updateMatrixWorld(true);let mn=1e9;c.tips.forEach(t=>{t.getWorldPosition(_v);mn=Math.min(mn,_v.y)});const by=c.root.getWorldPosition(_v).y;if(mn<by)c.body.position.y+=by-mn}
function applyPose(c,p){c.body.quaternion.set(0,0,0,1);c.chest.quaternion.set(0,0,0,1);c.head.quaternion.set(0,0,0,1);c.body.position.set(0,12/16,0);
 c.legR.rotation.set(p.legR[0],0,p.legR[1]);c.legL.rotation.set(p.legL[0],0,-p.legL[1]);c.armR.rotation.set(p.armR[0],0,p.armR[1]);c.armL.rotation.set(p.armL[0],0,-p.armL[1]);c.body.rotation.set(p.lean,0,p.roll)}
// ---------- 바람 캐릭터: 아이가 입은 옷 그대로 (dressup 그림) ----------
let BRF=null,BRI=null;
const ldImg=u=>new Promise(r=>{const i=new Image();i.onload=()=>r(i);i.onerror=()=>r(null);i.src=u});
async function buildBaram(){if(!ST.dress)return;try{
 const pk=await (await fetch('/pack/dressup/dress.json')).json();BRI={W:pk.W,H:pk.H,foot:pk.foot};const base=await ldImg('/pack/dressup/base.webp');if(!base)return;
 const its=[];for(const cat in ST.dress){const key=ST.dress[cat];const id=key.split(':')[1];const it=(pk.cats[cat]||[]).find(o=>String(o.id)===id);if(!it)continue;its.push({cat,v:it.v,img:await ldImg('/pack/dressup/'+it.s),mask:it.e?await ldImg('/pack/dressup/'+it.e):null})}
 const bw=pk.base.box[2],bh=pk.base.box[3];const F={};
 for(const m in pk.base.v){F[m]=pk.base.v[m].map((bf,fi)=>{const c=document.createElement('canvas');c.width=pk.W;c.height=pk.H;const g=c.getContext('2d');g.imageSmoothingEnabled=false;
  const at=(it,cb)=>{const v=it.v[m];if(!v||!it.img)return;const f=v[4][Math.min(fi,v[4].length-1)];cb(v,f)};
  its.filter(it=>it.cat==='cape').forEach(it=>at(it,(v,f)=>g.drawImage(it.img,f[0],f[1],v[2],v[3],v[0],v[1],v[2],v[3])));
  const bc=document.createElement('canvas');bc.width=pk.W;bc.height=pk.H;const bg=bc.getContext('2d');bg.imageSmoothingEnabled=false;bg.drawImage(base,bf[0],bf[1],bw,bh,0,0,bw,bh);bg.globalCompositeOperation='destination-out';
  its.filter(it=>it.mask&&it.cat!=='cape').forEach(it=>at(it,(v,f)=>bg.drawImage(it.mask,f[0],f[1],v[2],v[3],v[0],v[1],v[2],v[3])));g.drawImage(bc,0,0);
  ['shoes','weapon','shield','armor','coat','hair','helmet'].forEach(cat=>its.filter(it=>it.cat===cat).forEach(it=>at(it,(v,f)=>g.drawImage(it.img,f[0],f[1],v[2],v[3],v[0],v[1],v[2],v[3]))));
  const t=new THREE.CanvasTexture(c);t.magFilter=t.minFilter=THREE.NearestFilter;return t})}
 BRF=F;rBag()}catch(e){console.log('baram',e)}}
function mkBaram(){const mat=new THREE.SpriteMaterial({transparent:true,alphaTest:.4});const sp=new THREE.Sprite(mat);const I=BRI||{W:311,H:294,foot:[144.5,222]};const h=2.05*I.H/82;sp.scale.set(h*I.W/I.H,h,1);sp.center.set(I.foot[0]/I.W,1-I.foot[1]/I.H);const g=new THREE.Group();g.add(sp);return{root:g,sp,mat}}
function brDir(face){const dx=Math.sin(face),dz=Math.cos(face),sx=dx-dz,sz=dx+dz;return Math.abs(sx)>Math.abs(sz)?(sx>0?'r':'l'):(sz>0?'f':'b')}
function brFrame(b,face,moving,t,atk){if(!BRF)return;const d=brDir(face),k=d==='l'?'r':d;const fr=BRF[(atk?'atk_':'walk_')+k]||BRF['walk_'+k];if(!fr)return;const fi=atk?Math.min(fr.length-1,1):moving?Math.floor(t*7)%fr.length:0;if(b.mat.map!==fr[fi]){b.mat.map=fr[fi];b.mat.needsUpdate=true}const w=Math.abs(b.sp.scale.x);b.sp.scale.x=d==='l'?-w:w}
function drawBrThumb(cv){if(!BRF)return;const g=cv.getContext('2d');g.imageSmoothingEnabled=false;g.clearRect(0,0,cv.width,cv.height);g.drawImage(BRF.walk_f[0].image,110,120,90,110,0,0,cv.width,cv.height*1.1)}
// ---------- 방 안의 캐릭터들 ----------
const MAXC=4;const NPC=[];let PL=null;
function mkActor(key){const a={key,x:0,z:0,tx:0,tz:0,face:0,t:Math.random()*9,sk:null,skT:0,wait:Math.random()*2};if(key==='B'){a.b=mkBaram();a.root=a.b.root}else{a.c=mkChar(key);a.c.root.scale.setScalar(1.05);a.root=a.c.root}S.add(a.root);return a}
function doSkill(a,sk){if(a.sk)return;a.sk=sk;a.skT=0;a.tx=a.x;a.tz=a.z}
function tickActor(a,dt,speed){a.t+=dt;let moving=false,f=null;
 if(a.sk){a.skT+=dt;f=clipAt(a.sk,a.skT);if(f.done){// 끝난 자리·방향 이어 받기
   const r=clipRoot(f),y=clipYaw(f),c=Math.cos(a.face),s=Math.sin(a.face);const nx=a.x+r[0]*c+r[2]*s,nz=a.z-r[0]*s+r[2]*c;if(Math.abs(nx)<N/2-.3&&Math.abs(nz)<N/2-.3&&!blocked(nx,nz)){a.x=a.tx=nx;a.z=a.tz=nz}a.face+=y;a.sk=null;f=null}}
 if(!a.sk){const dx=a.tx-a.x,dz=a.tz-a.z,dist=Math.hypot(dx,dz);if(dist>.05){const sp=Math.min(dist,speed*dt),nx=a.x+dx/dist*sp,nz=a.z+dz/dist*sp;if(!blocked(nx,nz)&&Math.abs(nx)<N/2-.3&&Math.abs(nz)<N/2-.3){a.x=nx;a.z=nz;moving=true}else{a.tx=a.x;a.tz=a.z}a.face=Math.atan2(dx,dz)}}
 a.root.position.set(a.x,0,a.z);
 if(a.c){const c=a.c;if(f){c.root.rotation.y=a.face;applyClip(c,f)}else{const w=moving?Math.sin(a.t*11):0;applyPose(c,pz({legR:[-w*.9,0],legL:[w*.9,0],armR:[w*.9,0],armL:[-w*.9,0]}));c.body.position.y=12/16+(moving?Math.abs(Math.cos(a.t*11))*.05:Math.sin(a.t*3)*.015);
  c.root.rotation.y+=((a.face-c.root.rotation.y+Math.PI*3)%(Math.PI*2)-Math.PI)*Math.min(1,dt*14)}}
 else{let yaw=0,atk=false;if(f){const r=clipRoot(f),c=Math.cos(a.face),s=Math.sin(a.face);a.root.position.set(a.x+r[0]*c+r[2]*s,Math.max(0,r[1]-.75),a.z-r[0]*s+r[2]*c);yaw=clipYaw(f);atk=!!(f.k<.5?f.A:f.B)[31]}brFrame(a.b,a.face+yaw,moving,a.t,atk)}}
function setCtl(key){const old=PL;PL=mkActor(key);if(old){PL.x=old.x;PL.z=old.z;PL.tx=old.tx;PL.tz=old.tz;PL.face=old.face;S.remove(old.root)}else{PL.x=PL.tx=1;PL.z=PL.tz=2}
 BIG.setKey(key);$('curName').textContent=nameOf(key)}
function nameOf(k){return k==='B'?'⚔ 내 바람 캐릭터':(SKINS[k]?SKINS[k].n:'캐릭터')}
function addNpc(k){const a=mkActor(k);for(let i=0;i<20;i++){a.x=rnd2(-3,3);a.z=rnd2(-3,3);if(!blocked(a.x,a.z))break}a.tx=a.x;a.tz=a.z;NPC.push(a);return a}
function delNpc(k){const i=NPC.findIndex(n=>n.key===k);if(i<0)return;S.remove(NPC[i].root);NPC.splice(i,1)}
// ---------- 크게 보는 캐릭터 ----------
function preview(cv,key){const r=new THREE.WebGLRenderer({canvas:cv,antialias:true,alpha:true});const s=new THREE.Scene();s.add(new THREE.HemisphereLight(0xffffff,0xc8a878,1));const d=new THREE.DirectionalLight(0xffffff,.5);d.position.set(2,4,5);s.add(d);
 const cm=new THREE.PerspectiveCamera(30,cv.width/cv.height,.1,50);cm.position.set(0,1.2,5.2);cm.lookAt(0,1,0);const P={r,s,cm,t:0,c:null,b:null};
 P.setKey=k=>{if(P.c){s.remove(P.c.root);P.c=null}if(P.b){s.remove(P.b.root);P.b=null}if(k==='B'){P.b=mkBaram();s.add(P.b.root)}else{P.c=mkChar(k||'000');s.add(P.c.root)}};P.setKey(key);return P}
function tickPrev(P,dt){P.t+=dt;if(P.c){P.c.root.rotation.y=P.t*.9;const a=Math.sin(P.t*6)*.5;applyPose(P.c,pz({armR:[a,0],armL:[-a,0],legR:[-a*.6,0],legL:[a*.6,0]}))}if(P.b)brFrame(P.b,P.t*.9,true,P.t,false);P.r.render(P.s,P.cm)}
const BIG=preview($('bigcv'),'000');const DLG=[];
// ---------- 가구 배치 ----------
let editing=false,sel=null,tab='f';const placed=[];
const fits=(f,rot)=>rot%2?[f.d,f.w]:[f.w,f.d];
function cellsOf(p){const f=FUR[p.k];if(!f)return[];const [w,d]=fits(f,p.rot);const c=[];for(let i=0;i<w;i++)for(let j=0;j<d;j++)c.push((p.x+i)+','+(p.z+j));return c}
function freeAt(k,x,z,rot,ign){const f=FUR[k];if(!f)return false;const [w,d]=fits(f,rot);if(x<0||z<0||x+w>N||z+d>N)return false;const occ=new Set();placed.forEach(p=>{if(p.id!==ign)cellsOf(p).forEach(c=>occ.add(c))});for(let i=0;i<w;i++)for(let j=0;j<d;j++)if(occ.has((x+i)+','+(z+j)))return false;return true}
function placeObj(p){if(p.obj)S.remove(p.obj);const f=FUR[p.k];if(!f)return;const o=f.mk();const g=new THREE.Group();o.position.set(-f.w/2,0,-f.d/2);g.add(o);g.rotation.y=-p.rot*Math.PI/2;const [w,d]=fits(f,p.rot);g.position.set(p.x+w/2-N/2,0,p.z+d/2-N/2);p.obj=g;g.userData.pid=p.id;S.add(g)}
function findSpot(k){for(let z=0;z<N;z++)for(let x=0;x<N;x++)if(freeAt(k,x,z,0))return[x,z];return null}
function blocked(x,z){const cx=Math.floor(x+N/2),cz=Math.floor(z+N/2);return placed.some(p=>cellsOf(p).includes(cx+','+cz))}
// ---------- 저장 (방 모습만: 놓은 가구 · 놀러 온 친구 · 조종 캐릭터) ----------
let saveT=0;function saveRoom(){clearTimeout(saveT);saveT=setTimeout(()=>{const r={placed:placed.map(p=>({id:p.id,k:p.k,x:p.x,z:p.z,rot:p.rot})),npc:NPC.map(n=>n.key),ctl:PL?PL.key:''};if(AB)AB.saveRoom(JSON.stringify(r))},400)}
// ---------- 가방 ----------
function modal(html){const m=document.createElement('div');m.className='modal';m.innerHTML='<div class="card get">'+html+'</div>';m.onclick=e=>{if(e.target===m)closeM(m)};document.body.appendChild(m);return m}
function closeM(m){if(m._P){DLG.splice(DLG.indexOf(m._P),1);m._P.r.dispose();try{m._P.r.forceContextLoss()}catch(e){}}m.classList.add('out');setTimeout(()=>m.remove(),220)}
function confirmSell(name,pr,yes){const m=modal('<h3>정말로 판매하시겠습니까?</h3><p>「'+name+'」을(를) 팔고 '+pr+'골드를 받아요.<br>판 물건은 사라지고 되돌릴 수 없어요.</p><div class="row"><button class="btn p" id="y">예</button><button class="btn" id="n">아니오</button></div>');m.querySelector('#y').onclick=()=>{closeM(m);yes()};m.querySelector('#n').onclick=()=>closeM(m)}
function gacha(col,show){const m=modal('<div class="gc" style="--c:'+col+'"><div class="burst"></div><div class="cap"><i class="t"></i><i class="b"></i><b></b></div></div><p>두근두근…</p>');m.onclick=null;
 const g=m.querySelector('.gc');setTimeout(()=>{g.classList.add('open');try{navigator.vibrate&&navigator.vibrate(40)}catch(e){}},1000);setTimeout(()=>{show(m);m.onclick=e=>{if(e.target===m)closeM(m)}},1500)}
function myChars(){const L=['B'].filter(()=>!!ST.dress);return L.concat(ST.mc.filter(k=>SKINS[k]))}
function rBag(){const el=$('bag');el.innerHTML='';$('gold').textContent=ST.gold;
 if(tab==='a'){admin(el);return}
 if(tab==='c'){const L=myChars();if(!L.length)el.innerHTML='<div class="note">아직 캐릭터가 없어요. 🧑 캐릭터 뽑기를 해 보세요!</div>';
  L.forEach(k=>{const rm=NPC.some(n=>n.key===k),on=PL&&PL.key===k;const d=document.createElement('div');d.className='it'+(on||rm?' placed':'');d.dataset.b=rm?'방에 있음':'조종 중';d.innerHTML='<canvas class="px" width="64" height="64"></canvas>'+nameOf(k);const cv=d.querySelector('canvas');
   if(k==='B')drawBrThumb(cv);else{const g=cv.getContext('2d'),im=new Image();im.onload=()=>{g.imageSmoothingEnabled=false;g.drawImage(im,8,8,8,8,8,8,48,48);g.drawImage(im,40,8,8,8,8,8,48,48)};im.src=SKINS[k].u}
   d.onclick=()=>charMenu(k);el.appendChild(d)});return}
 const fur=ST.room.fur||[];if(!fur.length)el.innerHTML='<div class="note">아직 가구가 없어요. 등원하면 가구가 나올 수도 있고, 🪑 가구 뽑기(1골드)로도 얻어요!</div>';
 fur.forEach(b=>{const f=FUR[b.k];if(!f)return;const on=placed.some(p=>p.id===b.id);const d=document.createElement('div');d.className='it'+(on?' placed':'');d.dataset.b='방에 있음';d.innerHTML='<img alt="" src="'+thumb(b.k)+'">'+f.n+'<div class="rare" style="color:'+RCOL[f.r]+'">'+f.r+'</div>';d.onclick=()=>itemMenu(b);el.appendChild(d)})}
function itemMenu(b){const f=FUR[b.k],on=placed.some(p=>p.id===b.id),pr=RSELL[f.r]||1;
 const m=modal('<img alt="" src="'+thumb(b.k)+'"><h3>'+f.n+'</h3><p style="color:'+RCOL[f.r]+';font-weight:700">'+f.r+' 가구 · '+f.w+'×'+f.d+'칸</p><div class="row">'+(on?'<button class="btn b" id="m1">📦 방에서 빼기</button>':'<button class="btn on" id="m1">🏠 방에 놓기</button>')+'<button class="btn p" id="m2">판매하기 +'+pr+'G</button><button class="btn" id="m3">닫기</button></div>');
 m.querySelector('#m3').onclick=()=>closeM(m);
 m.querySelector('#m1').onclick=()=>{closeM(m);if(on){const i=placed.findIndex(p=>p.id===b.id);S.remove(placed[i].obj);placed.splice(i,1);toast('가방에 넣었어요')}else{const s=findSpot(b.k);if(!s){toast('방에 빈자리가 없어요');return}const p={id:b.id,k:b.k,x:s[0],z:s[1],rot:0};placed.push(p);placeObj(p);setEdit(true);select(p);toast('놓았어요! 바닥을 눌러 옮길 수 있어요')}saveRoom();rBag()};
 m.querySelector('#m2').onclick=()=>{closeM(m);confirmSell(f.n,pr,()=>{const i=placed.findIndex(p=>p.id===b.id);if(i>=0){S.remove(placed[i].obj);placed.splice(i,1);saveRoom()}call('sellFur',b.id,pr)})}}
function charMenu(k){const on=PL&&PL.key===k,inRoom=NPC.some(n=>n.key===k);
 const m=modal('<canvas class="big3d" width="440" height="480"></canvas><h3>'+nameOf(k)+'</h3><p>'+(k==='B'?'내가 입은 옷 그대로예요':'마크 캐릭터')+'</p><div class="row"><button class="btn on" id="m1"'+(on?' disabled':'')+'>'+(on?'✔ 조종 중':'🎮 조종하기')+'</button>'+(on?'':'<button class="btn b" id="m4">'+(inRoom?'📦 방에서 빼기':'🏠 방에 놓기 ('+(1+NPC.length)+'/'+MAXC+')')+'</button>')+(k==='B'?'':'<button class="btn p" id="m2"'+(on||inRoom?' disabled':'')+'>판매하기 +'+ST.mcSell+'G</button>')+'<button class="btn" id="m3">닫기</button></div>'+(on?'<p>조종 중인 캐릭터는 팔 수 없어요</p>':inRoom?'<p>방에서 빼면 팔 수 있어요</p>':'<p>방에는 최대 '+MAXC+'명까지 (내 캐릭터 포함)</p>'));
 const P=preview(m.querySelector('canvas'),k);DLG.push(P);m._P=P;
 m.querySelector('#m3').onclick=()=>closeM(m);
 m.querySelector('#m1').onclick=()=>{closeM(m);const prev=PL.key;if(inRoom){delNpc(k);addNpc(prev)}setCtl(k);if(k!=='B'&&AB)AB.setMcSkin(k);saveRoom();rBag();toast(nameOf(k)+' 조종!')};
 const b4=m.querySelector('#m4');if(b4)b4.onclick=()=>{closeM(m);if(inRoom){delNpc(k);toast('방에서 뺐어요')}else if(1+NPC.length>=MAXC){toast('방에는 '+MAXC+'명까지만 놓을 수 있어요')}else{addNpc(k);toast(nameOf(k)+' 놀러 왔어요!')}saveRoom();rBag()};
 const b2=m.querySelector('#m2');if(b2)b2.onclick=()=>{closeM(m);confirmSell(nameOf(k),ST.mcSell,()=>call('sellMc',k))}}
document.querySelectorAll('[data-t]').forEach(b=>b.onclick=()=>{tab=b.dataset.t;document.querySelectorAll('[data-t]').forEach(x=>x.classList.toggle('on',x===b));rBag()});
// 뽑기 (가구는 등급 확률로 고르고, 골드 계산은 앱이 서버에서)
function rollFur(){const r=Math.random();const by=r<.03?'레전드리':r<.1?'유니크':r<.25?'에픽':r<.55?'레어':'노말';let c=Object.keys(FUR).filter(k=>FUR[k].r===by);if(!c.length)c=Object.keys(FUR);return c[(Math.random()*c.length)|0]}
$('gacha').onclick=()=>{if(ST.gold<ST.furPrice){toast('골드가 모자라요');return}call('pullFur',rollFur(),ST.furPrice)};
$('cgacha').onclick=()=>{if(ST.gold<ST.mcPrice){toast('골드가 모자라요');return}call('pullMc')};
// ---------- 앱 다리 ----------
const PENDING={};function call(op,...a){if(!AB){toast('미리보기에선 저장이 안 돼요');return}AB[op](...a.map(String))}
window.onBridge=function(r){try{if(typeof r==='string')r=JSON.parse(r)}catch(e){return}if(r.op==='asset'){gotAsset(r);return}
 if(!r.ok){toast(r.err||'다시 해 주세요');return}
 if(r.op==='pullFur'){const f=FUR[r.k];if(f)gacha(RCOL[f.r],m=>{m.querySelector('.card').innerHTML='<div class="reveal"><p>🪑 가구 뽑기 결과!</p><img alt="" src="'+thumb(r.k)+'"><h3>'+f.n+'</h3><span class="shine" style="background:'+RCOL[f.r]+'">'+f.r+'</span><p>가방에 들어갔어요 · 눌러서 방에 놓아 보세요</p><div class="row"><button class="btn on" id="pl">🏠 바로 놓기</button><button class="btn" id="ok">확인</button></div></div>';
   m.querySelector('#ok').onclick=()=>closeM(m);m.querySelector('#pl').onclick=()=>{closeM(m);const b=(ST.room.fur||[]).slice().reverse().find(x=>x.k===r.k&&!placed.some(p=>p.id===x.id));if(b)itemMenu(b);else toast('잠깐 뒤에 가방에서 눌러 주세요')}})}
 if(r.op==='pullMc'){const k=r.k;gacha(r.dup?'#8a8fa3':'#f5b82e',m=>{m.querySelector('.card').innerHTML='<div class="reveal"><p>🧑 캐릭터 뽑기 결과!</p><canvas class="big3d" width="440" height="480"></canvas><h3>'+nameOf(k)+'</h3><p>'+(r.dup?'이미 있는 캐릭터예요 (골드 조금 돌려받음)':'✨ 새 캐릭터!')+'</p><div class="row"><button class="btn on" id="eq">🎮 바로 조종</button><button class="btn" id="ok">가방에 넣기</button></div></div>';
   const P=preview(m.querySelector('canvas'),k);DLG.push(P);m._P=P;m.querySelector('#ok').onclick=()=>{closeM(m);tab='c';document.querySelectorAll('[data-t]').forEach(x=>x.classList.toggle('on',x.dataset.t==='c'));rBag()};m.querySelector('#eq').onclick=()=>{closeM(m);setCtl(k);if(AB)AB.setMcSkin(k);saveRoom();rBag();toast(nameOf(k)+' 조종!')}})}
 if(r.op==='sellFur'||r.op==='sellMc')toast('판매했어요 +'+r.gold+'골드');
 if(r.op==='addAsset')toast('추가했어요!');if(r.op==='delAsset')toast('지웠어요')};
let first=true;
window.onState=function(s){try{if(typeof s==='string')s=JSON.parse(s)}catch(e){return}const hadDress=!!ST.dress;const dressKey=JSON.stringify(ST.dress);ST=Object.assign(ST,s);ST.room=ST.room||{};ST.room.fur=ST.room.fur||[];
 (ST.custom||[]).forEach(a=>{if(a.type==='fur'&&!FUR[a.k])defFur(Object.assign({custom:1},a));if(a.type==='skin'&&!SKINS[a.k])SKINS[a.k]={n:a.n||'새 캐릭터',u:a.b,custom:1}});
 $('title').textContent=(ST.name?ST.name+'의 ':'')+'마이룸';$('admTab').hidden=!ST.admin;
 if(first){first=false;loadRoom()}else{// 판 가구가 방에 남아 있으면 치움
  for(let i=placed.length-1;i>=0;i--)if(!ST.room.fur.some(f=>f.id===placed[i].id)){S.remove(placed[i].obj);placed.splice(i,1)}}
 if(JSON.stringify(ST.dress)!==dressKey)buildBaram();rBag()};
function loadRoom(){const r=ST.room;(r.placed||[]).forEach(p=>{if(FUR[p.k]&&ST.room.fur.some(f=>f.id===p.id)){const q={id:p.id,k:p.k,x:p.x,z:p.z,rot:p.rot||0};placed.push(q);placeObj(q)}});
 let ctl=r.ctl||ST.mcSkin||(ST.mc[0])||(ST.dress?'B':'000');if(ctl==='B'&&!ST.dress)ctl='000';if(ctl!=='B'&&!SKINS[ctl])ctl='000';setCtl(ctl);
 (r.npc||[]).slice(0,MAXC-1).forEach(k=>{if(k!==ctl&&(k==='B'?ST.dress:SKINS[k]))addNpc(k)});$('loading').remove()}
// ---------- 관리자: 복셀 가구(.obj + .png) · 캐릭터 스킨(.png) 추가 ----------
function admin(el){el.style.display='block';const cus=(ST.custom||[]);
 el.innerHTML='<div class="adm note" style="font-size:14px"><b>🪑 복셀 가구 추가</b> (MagicaVoxel에서 내보낸 .obj 와 같은 이름의 .png)<br><input type="file" id="aobj" accept=".obj"><input type="file" id="apng" accept="image/png"><br>이름 <input id="an" placeholder="예: 공주 침대" style="width:140px"> 등급 <select id="ar">'+Object.keys(RSELL).map(r=>'<option>'+r+'</option>').join('')+'</select><button class="btn on" id="aadd">추가</button>'
 +'<hr><b>🧑 캐릭터(마크 스킨 64×64 .png) 추가</b><br><input type="file" id="askin" accept="image/png"> 이름 <input id="asn" placeholder="예: 태권 소년" style="width:120px"><button class="btn on" id="asadd">추가</button>'
 +'<hr><b>내가 추가한 것</b> ('+cus.length+')<div id="alist"></div></div>';
 const al=el.querySelector('#alist');cus.forEach(a=>{const d=document.createElement('div');d.style.cssText='display:flex;gap:6px;align-items:center;margin:3px 0';d.innerHTML=(a.type==='fur'?'🪑 ':'🧑 ')+(a.n||a.k)+(a.r?' · '+a.r:'')+' <button class="btn">지우기</button>';d.querySelector('button').onclick=()=>{const m=modal('<h3>정말로 지우시겠습니까?</h3><p>「'+(a.n||a.k)+'」을(를) 모든 아이 목록에서 지워요.</p><div class="row"><button class="btn p" id="y">예</button><button class="btn" id="n">아니오</button></div>');m.querySelector('#y').onclick=()=>{closeM(m);call('delAsset',a.k)};m.querySelector('#n').onclick=()=>closeM(m)};al.appendChild(d)});
 el.querySelector('#aadd').onclick=async()=>{const fo=el.querySelector('#aobj').files[0],fp=el.querySelector('#apng').files[0];if(!fo||!fp){toast('.obj 와 .png 를 둘 다 골라 주세요');return}
  try{const def=await objToDef(await fo.text(),fp);def.n=el.querySelector('#an').value.trim()||fo.name.replace(/\.obj$/i,'');def.r=el.querySelector('#ar').value;def.type='fur';def.k='cf_'+Date.now().toString(36);if(def.b.length>900000){toast('너무 커요 (작은 가구로 해 주세요)');return}call('addAsset',JSON.stringify(def))}catch(e){toast('읽지 못했어요: '+e.message)}};
 el.querySelector('#asadd').onclick=async()=>{const f=el.querySelector('#askin').files[0];if(!f){toast('스킨 .png 를 골라 주세요');return}const u=await new Promise(r=>{const fr=new FileReader();fr.onload=()=>r(fr.result);fr.readAsDataURL(f)});const im=await ldImg(u);if(!im||im.width<64||im.width!==im.height){toast('64×64 마크 스킨이어야 해요');return}
  call('addAsset',JSON.stringify({type:'skin',k:'cs_'+Date.now().toString(36),n:el.querySelector('#asn').value.trim()||'새 캐릭터',b:u}))}}
// MagicaVoxel OBJ + 팔레트 PNG → .mbin (앱 안에서 바로 변환)
async function objToDef(txt,png){const im=await ldImg(URL.createObjectURL(png));const cv=document.createElement('canvas');cv.width=im.width;cv.height=im.height;const g=cv.getContext('2d');g.drawImage(im,0,0);const P=g.getImageData(0,0,im.width,1).data;const W=im.width;
 const V=[],T=[],tris=[];for(const l of txt.split('\n')){if(l.startsWith('v '))V.push(l.trim().split(/\s+/).slice(1,4).map(Number));else if(l.startsWith('vt '))T.push(+l.trim().split(/\s+/)[1]);else if(l.startsWith('f ')){const id=l.trim().split(/\s+/).slice(1).map(p=>p.split('/'));for(let k=1;k<id.length-1;k++)tris.push([id[0],id[k],id[k+1]])}}
 const n=tris.length*3,pos=new Float32Array(n*3),col=new Uint8Array(n*3);let o=0;for(const t of tris){const ci=Math.min(W-1,Math.floor(T[+t[0][1]-1]*W))*4;for(const p of t){const v=V[+p[0]-1];pos[o*3]=v[0];pos[o*3+1]=v[1];pos[o*3+2]=v[2];col[o*3]=P[ci];col[o*3+1]=P[ci+1];col[o*3+2]=P[ci+2];o++}}
 const mn=[1e9,1e9,1e9],mx=[-1e9,-1e9,-1e9];for(let i=0;i<n*3;i++){const a=i%3;mn[a]=Math.min(mn[a],pos[i]);mx[a]=Math.max(mx[a],pos[i])}const rg=mn.map((m,a)=>Math.max(mx[a]-m,1e-6));
 const buf=new ArrayBuffer(28+n*6+n*3),dv=new DataView(buf);dv.setUint32(0,n,true);for(let a=0;a<3;a++){dv.setFloat32(4+a*4,mn[a],true);dv.setFloat32(16+a*4,rg[a],true)}
 const q=new Int16Array(buf,28,n*3);for(let i=0;i<n*3;i++){const a=i%3;q[i]=Math.round((pos[i]-mn[a])/rg[a]*65535-32768)}new Uint8Array(buf,28+n*6,n*3).set(col);
 const u8=new Uint8Array(buf);let s='';for(let i=0;i<u8.length;i+=8192)s+=String.fromCharCode.apply(null,u8.subarray(i,i+8192));
 return{b:btoa(s),w:Math.max(1,Math.ceil(rg[0]/1.6-.2)),d:Math.max(1,Math.ceil(rg[2]/1.6-.2))}}
// ---------- 꾸미기 ----------
const selBox=new THREE.Mesh(new THREE.BoxGeometry(1,.06,1),new THREE.MeshBasicMaterial({color:0xff7aa8,transparent:true,opacity:.55}));selBox.visible=false;S.add(selBox);
function setEdit(on){editing=on;$('edit').classList.toggle('on',on);$('edit').textContent=on?'✔ 꾸미기 끝':'🛠 꾸미기';$('hint').textContent=on?'가구를 누르고, 바닥을 누르면 옮겨요':'바닥을 누르면 걸어가요';if(!on){select(null);saveRoom()}}
function select(p){sel=p;$('rot').hidden=$('back').hidden=!p;selBox.visible=!!p;if(p){const [w,d]=fits(FUR[p.k],p.rot);selBox.scale.set(w,1,d);selBox.position.set(p.x+w/2-N/2,.04,p.z+d/2-N/2)}}
$('edit').onclick=()=>setEdit(!editing);
$('rot').onclick=()=>{if(!sel)return;const nr=(sel.rot+1)%4;if(freeAt(sel.k,sel.x,sel.z,nr,sel.id)){sel.rot=nr;placeObj(sel);select(sel);saveRoom()}else toast('돌릴 자리가 없어요')};
$('back').onclick=()=>{if(!sel)return;S.remove(sel.obj);placed.splice(placed.indexOf(sel),1);select(null);saveRoom();rBag();toast('가방에 넣었어요')};
$('close').onclick=()=>{saveRoomNow();if(AB)AB.close()};
function saveRoomNow(){clearTimeout(saveT);const r={placed:placed.map(p=>({id:p.id,k:p.k,x:p.x,z:p.z,rot:p.rot})),npc:NPC.map(n=>n.key),ctl:PL?PL.key:''};if(AB)AB.saveRoom(JSON.stringify(r))}
const ray=new THREE.Raycaster(),nd=new THREE.Vector2(),plane=new THREE.Plane(new THREE.Vector3(0,1,0),0);
st.addEventListener('pointerdown',e=>{const r=st.getBoundingClientRect();nd.set((e.clientX-r.left)/r.width*2-1,-(e.clientY-r.top)/r.height*2+1);ray.setFromCamera(nd,cam);
 if(editing){const hits=ray.intersectObjects(placed.map(p=>p.obj).filter(Boolean),true);if(hits.length){let o=hits[0].object;while(o&&o.userData.pid==null)o=o.parent;const p=o&&placed.find(p=>p.id===o.userData.pid);if(p&&p!==sel){select(p);return}}}
 const pt=new THREE.Vector3();if(!ray.ray.intersectPlane(plane,pt))return;const cx=Math.floor(pt.x+N/2),cz=Math.floor(pt.z+N/2);
 if(editing&&sel){if(freeAt(sel.k,cx,cz,sel.rot,sel.id)){sel.x=cx;sel.z=cz;placeObj(sel);select(sel)}else toast('거기엔 놓을 수 없어요');return}
 if(PL){PL.tx=Math.max(-N/2+.4,Math.min(N/2-.4,pt.x));PL.tz=Math.max(-N/2+.4,Math.min(N/2-.4,pt.z))}});
const held={};document.querySelectorAll('[data-d]').forEach(b=>{const d=b.dataset.d;b.addEventListener('pointerdown',e=>{held[d]=1;e.preventDefault()});['pointerup','pointerleave','pointercancel'].forEach(ev=>b.addEventListener(ev,()=>held[d]=0))});
let ZOOM=2.9,camX=0,camZ=0;function fit(){const w=st.clientWidth,h=st.clientHeight;R.setSize(w,h,false);const a=w/h,v=ZOOM;cam.left=-v*a;cam.right=v*a;cam.top=v;cam.bottom=-v;cam.updateProjectionMatrix()}
$('zoom').onclick=()=>{ZOOM=ZOOM>4?2.9:4.6;$('zoom').textContent=ZOOM>4?'🔍 크게 보기':'🔍 방 전체';fit()};
function rSkills(){const el=$('skills');el.innerHTML='';CLIPS.forEach(sk=>{const b=document.createElement('button');b.className='btn';b.textContent=sk.n;b.onclick=()=>PL&&doSkill(PL,sk);el.appendChild(b)})}
// ---------- 루프 ----------
let last=performance.now();
function loop(now){requestAnimationFrame(loop);const dt=Math.min(.05,(now-last)/1000);last=now;
 if(PL){let mx=0,mz=0;if(held.up){mx-=1;mz-=1}if(held.down){mx+=1;mz+=1}if(held.left){mx-=1;mz+=1}if(held.right){mx+=1;mz-=1}if(mx||mz){const l=Math.hypot(mx,mz);PL.tx=PL.x+mx/l*.4;PL.tz=PL.z+mz/l*.4}tickActor(PL,dt,3.2)
  const k2=Math.min(1,dt*4);const tx=ZOOM>4?0:PL.x,tz=ZOOM>4?0:PL.z;camX+=(tx-camX)*k2;camZ+=(tz-camZ)*k2;cam.position.set(10+camX,10.5,10+camZ);cam.lookAt(camX,1.2,camZ)}
 for(const n of NPC){n.wait-=dt;if(!n.sk&&Math.hypot(n.tx-n.x,n.tz-n.z)<.1&&n.wait<=0){n.wait=rnd2(1.5,4);if(CLIPS.length&&Math.random()<.25)doSkill(n,CLIPS[(Math.random()*CLIPS.length)|0]);else for(let k=0;k<8;k++){const tx=rnd2(-N/2+.6,N/2-.6),tz=rnd2(-N/2+.6,N/2-.6);if(!blocked(tx,tz)){n.tx=tx;n.tz=tz;break}}}tickActor(n,dt,1.8)}
 if(sel)selBox.material.opacity=.4+.25*Math.sin(now/180);tickPrev(BIG,dt);DLG.forEach(P=>tickPrev(P,dt));R.render(S,cam)}
// ---------- 시작 ----------
(async()=>{const [fur,clips]=await Promise.all([fetch('fur.json').then(r=>r.json()),fetch('clips.json').then(r=>r.json())]);fur.forEach(defFur);CLIPS=clips;
 for(let i=0;i<102;i++){const k=String(i).padStart(3,'0');SKINS[k]={n:'캐릭터 '+(i+1),u:'../mc/'+k+'.png'}}
 addEventListener('resize',fit);fit();rSkills();requestAnimationFrame(loop);
 if(AB)AB.ready();else window.onState({name:'미리보기',gold:30,mc:['022','025'],room:{fur:[{id:1,k:'bedd'},{id:2,k:'tv'}],placed:[{id:1,k:'bedd',x:0,z:0,rot:0}],npc:['025']}})})();
