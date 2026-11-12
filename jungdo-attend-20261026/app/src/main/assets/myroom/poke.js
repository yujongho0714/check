// 마이룸 포켓몬: 출석 뽑기로 얻은 포켓몬이 방을 돌아다니고, 누르면 기술을 써요.
// 그림·이펙트는 포켓몬 어드벤처 사이트(yujongho0714.github.io/attack/poke)에서 가져와요.
(function(){
const HB='https://yujongho0714.github.io/attack/poke/';
const TL=new THREE.TextureLoader();TL.setCrossOrigin('anonymous');
const PK=[];let FXM=null,want='',FOL=null;const FXL=[];
const tex=u=>new Promise(r=>TL.load(u,t=>{t.magFilter=t.minFilter=THREE.NearestFilter;t.generateMipmaps=false;if(THREE.sRGBEncoding)t.encoding=THREE.sRGBEncoding;r(t)},undefined,()=>r(null)));
const j=u=>fetch(u).then(r=>r.ok?r.json():null).catch(()=>null);
const fkey=()=>'mr-follow:'+(ST&&ST.name||'');
function lsGet(k){try{return localStorage.getItem(k)||''}catch(e){return ''}}function lsSet(k,v){try{localStorage.setItem(k,v)}catch(e){}}
const RAR=['일반','희귀','영웅','전설'],RC=['#9aa','#4aa8ff','#c070ff','#ffb020'];

async function mk(it){const d=String(it.dex).padStart(4,'0'),b=HB+'pk/sprite/'+d+'/';const meta=await j(b+'pmd.json');if(!meta)return null;
  const T={};await Promise.all(Object.keys(meta.anims).map(async st=>{const t=await tex(b+meta.anims[st].f+'-Anim.png');if(t)T[st]=t}));if(!T.idle)return null;
  const mat=new THREE.MeshBasicMaterial({map:T.idle,transparent:true,alphaTest:.2,side:THREE.DoubleSide});if(it.sh)mat.color.setHSL(.12,.6,.75);
  const m=new THREE.Mesh(new THREE.PlaneGeometry(1,1),mat);const root=new THREE.Group();root.add(m);
  // 그림자
  const sh=new THREE.Mesh(new THREE.CircleGeometry(.6,20),new THREE.MeshBasicMaterial({color:0,transparent:true,opacity:.25,depthWrite:false}));sh.rotation.x=-Math.PI/2;sh.position.y=.02;root.add(sh);
  const st=meta.stats||[];const bst=st.reduce((a,b)=>a+b,0)||300;const H=1.9+Math.min(.5,(bst-250)/900)+(it.rar||0)*.06;
  const p={it,meta,T,m,root,sh,H,u:H/48,x:0,z:0,tx:0,tz:0,face:0,cur:'idle',fi:0,ft:0,once:null,wait:Math.random()*2,t:0};
  for(let i=0;i<20;i++){p.x=rnd2(-N/2+.8,N/2-.8);p.z=rnd2(-N/2+.8,N/2-.8);if(!blocked(p.x,p.z))break}p.tx=p.x;p.tz=p.z;
  root.userData.pk=p;m.userData.pk=p;S.add(root);return p}
function play(p,k,cb){if(!p.T[k])k=k==='shoot'&&p.T.attack?'attack':k==='attack'&&p.T.shoot?'shoot':'idle';p.cur=k;p.fi=0;p.ft=0;p.once=cb||(()=>{})}
function show(p){const A=p.meta.anims[p.cur]||p.meta.anims.idle,t=p.T[p.cur]||p.T.idle;if(p.m.material.map!==t){p.m.material.map=t;p.m.material.needsUpdate=true}
  // 카메라 쪽에서 본 방향 → 8방향 줄
  const tcx=cam.position.x-p.x,tcz=cam.position.z-p.z,l=Math.hypot(tcx,tcz)||1,ux=tcx/l,uz=tcz/l,fx=Math.sin(p.face),fz=Math.cos(p.face);
  const a=Math.atan2(fx*uz-fz*ux,fx*ux+fz*uz);const row=((Math.round(a/(Math.PI/4))%8)+8)%8;
  t.repeat.set(1/A.n,1/8);t.offset.set((p.fi%A.n)/A.n,1-(row+1)/8);p.m.scale.set(A.w*p.u,A.h*p.u,1);p.m.position.y=4*p.u;p.m.rotation.set(0,Math.atan2(tcx,tcz),0)}
function tick(p,dt){p.t+=dt;let moving=false;
  if(!p.once){const dx=p.tx-p.x,dz=p.tz-p.z,d=Math.hypot(dx,dz);if(d>.05){const sp=Math.min(d,(p===FOL?3.2:1.7)*dt),nx=p.x+dx/d*sp,nz=p.z+dz/d*sp;if(Math.abs(nx)<N/2-.3&&Math.abs(nz)<N/2-.3&&(p===FOL||!blocked(nx,nz))){p.x=nx;p.z=nz;moving=true}else{p.tx=p.x;p.tz=p.z}p.face=Math.atan2(dx,dz)}
    const k=moving?'walk':'idle';if(p.cur!==k&&p.T[k]){p.cur=k;p.fi=0;p.ft=0}}
  const A=p.meta.anims[p.cur]||p.meta.anims.idle;p.ft+=dt*60;let need=(A.d||[])[p.fi%A.n]||4;while(p.ft>=need){p.ft-=need;p.fi++;if(p.fi>=A.n){if(p.once){const cb=p.once;p.once=null;p.cur='idle';p.fi=0;cb();break}p.fi=0}need=(A.d||[])[p.fi%A.n]||4}
  // 혼자 놀기 / 따라다니기
  if(p===FOL&&typeof PL!=='undefined'&&PL){const bx=PL.x-Math.sin(PL.face)*1.1,bz=PL.z-Math.cos(PL.face)*1.1;if(Math.hypot(bx-p.x,bz-p.z)>.6){p.tx=bx;p.tz=bz}}
  else if(!p.once&&p!==SELP&&Math.hypot(p.tx-p.x,p.tz-p.z)<.1){p.wait-=dt;if(p.wait<=0){p.wait=rnd2(1.5,5);if(Math.random()<.15)useMove(p,pickMove(p),true);else for(let k=0;k<8;k++){const tx=rnd2(-N/2+.6,N/2-.6),tz=rnd2(-N/2+.6,N/2-.6);if(!blocked(tx,tz)){p.tx=tx;p.tz=tz;break}}}}
  p.root.position.set(p.x,0,p.z);show(p)}

// ---------- 기술 ----------
function movesOf(p){const L=(p.meta.moves||[]).map(m=>({id:m.id,name:m.name,type:m.type}));(ST.pmv||[]).forEach(m=>{if(!L.some(x=>x.id===m.id))L.push(Object.assign({card:1},m))});return L}
function pickMove(p){const L=movesOf(p);return L[(Math.random()*L.length)|0]}
const FXT={};async function fxTex(f){if(!(f in FXT))FXT[f]=tex(HB+'vfx/'+f+'.png');return FXT[f]}
async function useMove(p,mv,quiet){if(!mv||p.once)return;if(!FXM)FXM=await j(HB+'fxmap.json')||{};
  const phys=/punch|kick|bite|tackle|slash|claw|fang|cut|scissor|strike|hammer|crash|slam|ace|jab|chop|tail|horn|body|headbutt|outrage|uturn|quick/.test(mv.id);
  if(typeof PL!=='undefined'&&PL&&!quiet){p.face=Math.atan2(cam.position.x-p.x,cam.position.z-p.z)+.9}
  play(p,phys?'attack':'shoot');if(!quiet)pop(p,mv.name+'!');
  const e=FXM[mv.id];if(!e)return;const t0=await fxTex(e[0]);if(!t0)return;const t=t0.clone();t.needsUpdate=true;const [c,r]=[e[1],e[2]];t.repeat.set(1/c,1/r);
  // 밝은 방바닥에서도 잘 보이게: 원래 색(보통 섞기) + 은은한 빛(더하기) + 바닥 원 + 화면 번쩍
  const TC={NORMAL:'#a8a878',FIRE:'#ff6a20',WATER:'#3a8cff',ELECTRIC:'#ffd21a',GRASS:'#3cc85a',ICE:'#7ae0ff',FIGHTING:'#d0402a',POISON:'#b040c0',GROUND:'#d8a040',FLYING:'#9a8cff',PSYCHIC:'#ff4a90',BUG:'#a0c020',ROCK:'#b89838',GHOST:'#7050a8',DRAGON:'#6a3cff',DARK:'#584838',STEEL:'#a0a0c8',FAIRY:'#ff90c0'};const col=TC[mv.type]||'#ffffff';
  const sp=new THREE.Sprite(new THREE.SpriteMaterial({map:t,transparent:true,depthWrite:false,depthTest:false}));
  const glow=new THREE.Sprite(new THREE.SpriteMaterial({map:t,transparent:true,depthWrite:false,depthTest:false,blending:THREE.AdditiveBlending,opacity:.45,color:new THREE.Color(col)}));
  const dist=phys?1.3:3,s=(phys?3.6:5.2)*(quiet?.8:1),X=p.x+Math.sin(p.face)*dist,Z=p.z+Math.cos(p.face)*dist;sp.scale.set(s,s,1);glow.scale.set(s*1.15,s*1.15,1);sp.position.set(X,p.H*.5,Z);glow.position.copy(sp.position);sp.renderOrder=21;glow.renderOrder=20;S.add(glow);S.add(sp);
  const ring=new THREE.Mesh(new THREE.RingGeometry(.2,.45,40),new THREE.MeshBasicMaterial({color:new THREE.Color(col),transparent:true,opacity:.85,depthWrite:false,side:THREE.DoubleSide}));ring.rotation.x=-Math.PI/2;ring.position.set(X,.04,Z);S.add(ring);
  FXL.push({sp,glow,ring,t,n:c*r,c,r,k:0,fps:20,del:phys?.15:.25});
  if(!quiet){try{const f=document.createElement('div');f.style.cssText='position:fixed;inset:0;pointer-events:none;z-index:50;background:'+col+';opacity:.35;transition:opacity .35s';document.body.appendChild(f);requestAnimationFrame(()=>f.style.opacity='0');setTimeout(()=>f.remove(),400)}catch(e){}SHAKE=.35}}
let SHAKE=0;
let SJX=0,SJZ=0;function tickFx(dt){cam.position.x-=SJX;cam.position.z-=SJZ;SJX=SJZ=0;if(SHAKE>0){SHAKE=Math.max(0,SHAKE-dt);const a=SHAKE*.25;SJX=(Math.random()-.5)*a;SJZ=(Math.random()-.5)*a;cam.position.x+=SJX;cam.position.z+=SJZ}
  for(let i=FXL.length-1;i>=0;i--){const F=FXL[i];if(F.del>0){F.del-=dt;F.sp.visible=F.glow.visible=false;continue}F.sp.visible=F.glow.visible=true;F.k+=dt*F.fps;const k=Math.floor(F.k);
    if(k>=F.n){[F.sp,F.glow,F.ring].forEach(o=>{S.remove(o);o.material.dispose()});F.ring.geometry.dispose();F.t.dispose();FXL.splice(i,1);continue}
    F.t.offset.set((k%F.c)/F.c,1-(Math.floor(k/F.c)+1)/F.r);const fo=k/F.n>.8?1-(k/F.n-.8)/.2:1;F.sp.material.opacity=fo;F.glow.material.opacity=.45*fo;const g=1+F.k/F.n*5;F.ring.scale.set(g,g,g);F.ring.material.opacity=.85*(1-F.k/F.n)}}
// 머리 위 말풍선
function pop(p,txt){const c=document.createElement('canvas');c.width=256;c.height=64;const g=c.getContext('2d');g.fillStyle='rgba(20,20,40,.85)';g.strokeStyle='#ffd23f';g.lineWidth=5;g.beginPath();g.roundRect?g.roundRect(4,8,248,48,20):g.rect(4,8,248,48);g.fill();g.stroke();g.fillStyle='#ffd23f';g.font='900 34px sans-serif';g.textAlign='center';g.textBaseline='middle';g.fillText(txt,128,33,236);
  const t=new THREE.CanvasTexture(c);const sp=new THREE.Sprite(new THREE.SpriteMaterial({map:t,transparent:true,depthTest:false}));sp.scale.set(3.6,.9,1);sp.position.set(0,p.H+.5,0);sp.renderOrder=30;p.root.add(sp);setTimeout(()=>{p.root.remove(sp);t.dispose();sp.material.dispose()},1400)}

// ---------- 누르면 선택: 아래에 기술 버튼이 떠서 여러 번 쓸 수 있어요 ----------
let SELP=null,BAR=null;
const TN={NORMAL:'노말',FIRE:'불꽃',WATER:'물',ELECTRIC:'전기',GRASS:'풀',ICE:'얼음',FIGHTING:'격투',POISON:'독',GROUND:'땅',FLYING:'비행',PSYCHIC:'에스퍼',BUG:'벌레',ROCK:'바위',GHOST:'고스트',DRAGON:'드래곤',DARK:'악',STEEL:'강철',FAIRY:'페어리'};
function unsel(){SELP=null;if(BAR){BAR.remove();BAR=null}}
function menu(p){unsel();SELP=p;const it=p.it,L=movesOf(p);
  const bar=document.createElement('div');BAR=bar;
  bar.style.cssText='position:fixed;left:0;right:0;bottom:0;z-index:50;background:rgba(10,12,28,.92);color:#fff;padding:10px 10px calc(10px + env(safe-area-inset-bottom,0px));border-radius:18px 18px 0 0;text-align:center;box-shadow:0 -6px 24px rgba(0,0,0,.45)';
  bar.innerHTML=`<div style="font-size:19px;font-weight:900;margin-bottom:6px">${it.sh?'✨':''}${p.meta.name} <small style="color:${RC[it.rar||0]}">[${RAR[it.rar||0]}] Lv.${it.lv||5}</small> <span style="font-size:14px;font-weight:600;opacity:.8">· 기술을 눌러 보세요!</span></div>
   <div style="display:flex;flex-wrap:wrap;gap:8px;justify-content:center">${L.map((m,i)=>`<button class="btn" data-m="${i}" style="font-size:18px;padding:12px 16px;min-height:52px">${m.card?'📀 ':''}${m.name} <small>${TN[m.type]||''}</small></button>`).join('')}</div>
   <div style="display:flex;gap:8px;justify-content:center;margin-top:10px"><button class="btn" id="pkF" style="font-size:16px;padding:10px 14px">${FOL===p?'🐾 그만 따라오기':'🐾 나를 따라오게'}</button><button class="btn" id="pkX" style="font-size:16px;padding:10px 14px">✕ 닫기</button></div>`;
  document.body.appendChild(bar);
  bar.addEventListener('pointerdown',e=>e.stopPropagation());
  bar.querySelectorAll('[data-m]').forEach(b=>b.onclick=()=>{useMove(p,L[+b.dataset.m])});
  bar.querySelector('#pkX').onclick=unsel;
  bar.querySelector('#pkF').onclick=()=>{FOL=FOL===p?null:p;lsSet(fkey(),FOL?FOL.it.key:'');unsel();if(FOL){pop(p,'따라갈게!');if(typeof PL==='undefined'||!PL)toast('캐릭터를 조종하면 졸졸 따라와요')}}}

// ---------- 오른쪽 가방: ⚡ 포켓몬 탭 ----------
let PKBUSY=0;
window.PKBag=function(el){const fp=!(ST.pfree||'').includes('P'),fm=!(ST.pfree||'').includes('M'),pP=ST.pP||10,pM=ST.pM||5;
  const L=(ST.poke||[]).slice().sort((a,b)=>(b.rar||0)-(a.rar||0));
  el.innerHTML=`<div style="grid-column:1/-1;display:flex;gap:6px;flex-wrap:wrap;margin-bottom:6px"><button class="btn g" id="pkPullP">⚡ 포켓몬 뽑기 ${fp?'(첫 1회 무료)':pP+'G'}</button><button class="btn g" id="pkPullM">📀 기술 뽑기 ${fm?'(첫 1회 무료)':pM+'G'}</button></div>
   <div class="note" style="grid-column:1/-1;margin:0 0 6px">포켓몬을 누르면 기술을 쓰고, 따라오게 할 수 있어요. 내 폰 게임으로 받기는 보관함 → ⚡ 포켓몬에서!</div>`+
   (L.length?'':'<div class="note" style="grid-column:1/-1">아직 포켓몬이 없어요. ⚡ 포켓몬 뽑기를 해 보세요!</div>');
  L.forEach(it=>{const d=document.createElement('div');d.className='it';const key=it.dex+(it.sh?'s':''),inRoom=PK.find(p=>p.it.key===key);if(FOL&&FOL.it.key===key){d.className+=' placed';d.dataset.b='따라옴'}
    const d4=String(it.dex).padStart(4,'0');d.innerHTML=`<img src="${HB}pk/portrait/${d4}/Normal.png" style="width:56px;height:56px;image-rendering:pixelated;border-radius:8px;background:#fff" onerror="this.style.visibility='hidden'"><br><b style="color:${RC[it.rar||0]}">${it.sh?'✨':''}${it.name||('No.'+it.dex)}</b><br><small>Lv.${it.lv||5} · ${RAR[it.rar||0]}</small>`;
    d.onclick=()=>{if(inRoom)menu(inRoom);else toast('방에는 8마리까지 나와요')};el.appendChild(d)});
  (ST.pmv||[]).forEach(m=>{const d=document.createElement('div');d.className='it';d.innerHTML=`<div style="font-size:36px">📀</div><b>${m.name}</b><br><small>기술 카드 · 포켓몬을 눌러 써요</small>`;el.appendChild(d)});
  const pull=k=>{if(PKBUSY)return;const free=k==='P'?fp:fm,pr=k==='P'?pP:pM;if(!free&&ST.gold<pr){toast('골드가 모자라요');return}if(!window.AB){toast('미리보기에선 안 돼요');return}PKBUSY=1;AB.pokePull(k);setTimeout(()=>PKBUSY=0,8000)};
  el.querySelector('#pkPullP').onclick=()=>pull('P');el.querySelector('#pkPullM').onclick=()=>pull('M')};
window.PKPulled=async function(r){PKBUSY=0;if(!r.ok){toast(r.err||'뽑지 못했어요');return}
  const d4=String(r.idx).padStart(4,'0');
  if(window.CardPack)await CardPack.open({rar:r.rar,sh:r.sh,kind:r.k,name:r.name,sub:r.k==='P'?'Lv.'+r.lv:'기술 카드',img:r.k==='P'?HB+'pk/portrait/'+d4+'/Normal.png':'',base:'../cardpack/'});
  toast('🎒 가방에 들어갔어요! '+r.name)};

// ---------- 연결 ----------
window.PKTick=function(dt){for(const p of PK)tick(p,dt);tickFx(dt)};
window.PKTap=function(ray){const hits=ray.intersectObjects(PK.map(p=>p.m),false);if(!hits.length)return false;menu(hits[0].object.userData.pk);return true};
window.PKSync=async function(){const L=(ST.poke||[]).map(x=>Object.assign({key:x.dex+(x.sh?'s':'')},x));const uniq=[];L.sort((a,b)=>(b.rar||0)-(a.rar||0)).forEach(x=>{if(!uniq.some(u=>u.key===x.key))uniq.push(x)});const top=uniq.slice(0,8);
  const k=top.map(x=>x.key).join(',');if(k===want)return;want=k;
  for(let i=PK.length-1;i>=0;i--)if(!top.some(x=>x.key===PK[i].it.key)){S.remove(PK[i].root);PK.splice(i,1)}
  for(const it of top)if(!PK.some(p=>p.it.key===it.key)){const p=await mk(it);if(p&&want===k)PK.push(p);else if(p)S.remove(p.root)}
  const fk=lsGet(fkey());FOL=PK.find(p=>p.it.key===fk)||null};
})();
