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
  const sh=new THREE.Mesh(new THREE.CircleGeometry(.28,16),new THREE.MeshBasicMaterial({color:0,transparent:true,opacity:.25,depthWrite:false}));sh.rotation.x=-Math.PI/2;sh.position.y=.02;root.add(sh);
  const st=meta.stats||[];const bst=st.reduce((a,b)=>a+b,0)||300;const H=.9+Math.min(.7,(bst-250)/700)+(it.rar||0)*.08;
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
  if(!p.once){const dx=p.tx-p.x,dz=p.tz-p.z,d=Math.hypot(dx,dz);if(d>.05){const sp=Math.min(d,(p===FOL?2.6:1.3)*dt),nx=p.x+dx/d*sp,nz=p.z+dz/d*sp;if(Math.abs(nx)<N/2-.3&&Math.abs(nz)<N/2-.3&&(p===FOL||!blocked(nx,nz))){p.x=nx;p.z=nz;moving=true}else{p.tx=p.x;p.tz=p.z}p.face=Math.atan2(dx,dz)}
    const k=moving?'walk':'idle';if(p.cur!==k&&p.T[k]){p.cur=k;p.fi=0;p.ft=0}}
  const A=p.meta.anims[p.cur]||p.meta.anims.idle;p.ft+=dt*60;let need=(A.d||[])[p.fi%A.n]||4;while(p.ft>=need){p.ft-=need;p.fi++;if(p.fi>=A.n){if(p.once){const cb=p.once;p.once=null;p.cur='idle';p.fi=0;cb();break}p.fi=0}need=(A.d||[])[p.fi%A.n]||4}
  // 혼자 놀기 / 따라다니기
  if(p===FOL&&typeof PL!=='undefined'&&PL){const bx=PL.x-Math.sin(PL.face)*1.1,bz=PL.z-Math.cos(PL.face)*1.1;if(Math.hypot(bx-p.x,bz-p.z)>.6){p.tx=bx;p.tz=bz}}
  else if(!p.once&&Math.hypot(p.tx-p.x,p.tz-p.z)<.1){p.wait-=dt;if(p.wait<=0){p.wait=rnd2(1.5,5);if(Math.random()<.15)useMove(p,pickMove(p),true);else for(let k=0;k<8;k++){const tx=rnd2(-N/2+.6,N/2-.6),tz=rnd2(-N/2+.6,N/2-.6);if(!blocked(tx,tz)){p.tx=tx;p.tz=tz;break}}}}
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
  const sp=new THREE.Sprite(new THREE.SpriteMaterial({map:t,transparent:true,depthWrite:false,depthTest:false,blending:THREE.AdditiveBlending}));
  const dist=phys?.6:1.5,s=(phys?1.6:2.4)*(quiet?.7:1);sp.scale.set(s,s,1);sp.position.set(p.x+Math.sin(p.face)*dist,p.H*.55,p.z+Math.cos(p.face)*dist);sp.renderOrder=20;S.add(sp);
  FXL.push({sp,t,n:c*r,c,r,k:0,fps:20,del:phys?.15:.25})}
function tickFx(dt){for(let i=FXL.length-1;i>=0;i--){const F=FXL[i];if(F.del>0){F.del-=dt;F.sp.visible=false;continue}F.sp.visible=true;F.k+=dt*F.fps;const k=Math.floor(F.k);if(k>=F.n){S.remove(F.sp);F.sp.material.dispose();F.t.dispose();FXL.splice(i,1);continue}F.t.offset.set((k%F.c)/F.c,1-(Math.floor(k/F.c)+1)/F.r);F.sp.material.opacity=k/F.n>.8?1-(k/F.n-.8)/.2:1}}
// 머리 위 말풍선
function pop(p,txt){const c=document.createElement('canvas');c.width=256;c.height=64;const g=c.getContext('2d');g.fillStyle='rgba(0,0,0,.65)';g.beginPath();g.roundRect?g.roundRect(4,8,248,48,20):g.rect(4,8,248,48);g.fill();g.fillStyle='#fff';g.font='900 30px sans-serif';g.textAlign='center';g.textBaseline='middle';g.fillText(txt,128,33,236);
  const t=new THREE.CanvasTexture(c);const sp=new THREE.Sprite(new THREE.SpriteMaterial({map:t,transparent:true,depthTest:false}));sp.scale.set(1.6,.4,1);sp.position.set(0,p.H+.35,0);sp.renderOrder=30;p.root.add(sp);setTimeout(()=>{p.root.remove(sp);t.dispose();sp.material.dispose()},1400)}

// ---------- 누르면 메뉴 ----------
function menu(p){const it=p.it,L=movesOf(p),TN={NORMAL:'노말',FIRE:'불꽃',WATER:'물',ELECTRIC:'전기',GRASS:'풀',ICE:'얼음',FIGHTING:'격투',POISON:'독',GROUND:'땅',FLYING:'비행',PSYCHIC:'에스퍼',BUG:'벌레',ROCK:'바위',GHOST:'고스트',DRAGON:'드래곤',DARK:'악',STEEL:'강철',FAIRY:'페어리'};
  const md=modal(`<h3 style="margin:4px 0">${it.sh?'✨':''}${p.meta.name} <small style="color:${RC[it.rar||0]}">[${RAR[it.rar||0]}] Lv.${it.lv||5}</small></h3>
   <img src="${HB}pk/portrait/${String(it.dex).padStart(4,'0')}/Happy.png" onerror="this.src='${HB}pk/portrait/${String(it.dex).padStart(4,'0')}/Normal.png'" style="width:80px;height:80px;image-rendering:pixelated;border-radius:12px;background:#fff">
   <p style="margin:6px 0">기술을 눌러 보세요!</p><div style="display:flex;flex-wrap:wrap;gap:6px;justify-content:center">${L.map((m,i)=>`<button class="btn" data-m="${i}">${m.card?'📀 ':''}${m.name} <small>${TN[m.type]||''}</small></button>`).join('')}</div>
   <div style="display:flex;gap:6px;justify-content:center;margin-top:10px"><button class="btn" id="pkF">${FOL===p?'🐾 그만 따라오기':'🐾 나를 따라오게'}</button><button class="btn" id="pkX">닫기</button></div>`);
  md.querySelectorAll('[data-m]').forEach(b=>b.onclick=()=>{closeM(md);useMove(p,L[+b.dataset.m])});
  md.querySelector('#pkX').onclick=()=>closeM(md);
  md.querySelector('#pkF').onclick=()=>{FOL=FOL===p?null:p;lsSet(fkey(),FOL?FOL.it.key:'');closeM(md);if(FOL){pop(p,'따라갈게!');if(typeof PL==='undefined'||!PL)(window.toast||alert)('캐릭터를 조종하면 졸졸 따라와요')}}}

// ---------- 연결 ----------
window.PKTick=function(dt){for(const p of PK)tick(p,dt);tickFx(dt)};
window.PKTap=function(ray){const hits=ray.intersectObjects(PK.map(p=>p.m),false);if(!hits.length)return false;menu(hits[0].object.userData.pk);return true};
window.PKSync=async function(){const L=(ST.poke||[]).map(x=>Object.assign({key:x.dex+(x.sh?'s':'')},x));const uniq=[];L.sort((a,b)=>(b.rar||0)-(a.rar||0)).forEach(x=>{if(!uniq.some(u=>u.key===x.key))uniq.push(x)});const top=uniq.slice(0,8);
  const k=top.map(x=>x.key).join(',');if(k===want)return;want=k;
  for(let i=PK.length-1;i>=0;i--)if(!top.some(x=>x.key===PK[i].it.key)){S.remove(PK[i].root);PK.splice(i,1)}
  for(const it of top)if(!PK.some(p=>p.it.key===it.key)){const p=await mk(it);if(p&&want===k)PK.push(p);else if(p)S.remove(p.root)}
  const fk=lsGet(fkey());FOL=PK.find(p=>p.it.key===fk)||null};
})();
