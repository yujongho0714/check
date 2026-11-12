// 카드팩 뜯기 연출 (게임 + 출석앱 공용, 다른 코드에 의존하지 않음)
// CardPack.open({rar:0..3, sh:0/1, kind:'P'|'M', name, sub, img(초상화 url), col(타입색), base:'cardpack/'}) → Promise
const CardPack=(()=>{
  const COL=['#c9d3dc','#4aa8ff','#c070ff','#ffb020','#ff3b6b','#22e6d0','#ffffff'],COL2=['#8a98a6','#1d5fd0','#6a1fc0','#ff6a00','#a0002a','#0a7a8a','#ff2aa0'],RN=['일반','희귀','영웅','전설','신화','환상','유일'];
  const TAPS=[1,1,3,3,4,5,6];
  let AC=null;function tone(f,d,ty,v,at){try{AC=AC||new (window.AudioContext||window.webkitAudioContext)();if(AC.state!=='running')AC.resume();const o=AC.createOscillator(),g=AC.createGain(),t=AC.currentTime+(at||0);o.type=ty||'sine';o.frequency.setValueAtTime(f,t);g.gain.setValueAtTime(v||.12,t);g.gain.exponentialRampToValueAtTime(.001,t+d);o.connect(g);g.connect(AC.destination);o.start(t);o.stop(t+d+.02)}catch(e){}}
  function noise(d,v){try{AC=AC||new (window.AudioContext||window.webkitAudioContext)();const n=AC.sampleRate*d,b=AC.createBuffer(1,n,AC.sampleRate),a=b.getChannelData(0);for(let i=0;i<n;i++)a[i]=(Math.random()*2-1)*(1-i/n);const s=AC.createBufferSource(),g=AC.createGain();g.gain.value=v||.2;s.buffer=b;s.connect(g);g.connect(AC.destination);s.start()}catch(e){}}
  const S={tap:r=>tone(300+r*120,.08,'square',.06),tear:()=>noise(.35,.25),
    rev:r=>{const N=[[523,659,784],[523,659,784,1046],[392,523,659,784,1046],[262,392,523,659,784,1046,1318],[262,330,392,523,659,784,1046,1318,1568]][Math.min(4,r)];N.forEach((f,i)=>tone(f,.25+(i==N.length-1?.5:0),'triangle',.1,i*.09))},
    zap:()=>tone(80+Math.random()*60,.15,'sawtooth',.08),boom:()=>{noise(.8,.35);tone(55,.8,'sine',.3)}};
  function loadImg(u,ms){return new Promise(r=>{if(!u)return r(null);const i=new Image();let d=0;const ok=v=>{if(!d){d=1;r(v)}};i.onload=()=>ok(i);i.onerror=()=>ok(null);i.src=u;setTimeout(()=>ok(i.complete&&i.naturalWidth?i:null),ms||1200)})}
  function rr(c,x,y,w,h,r){c.beginPath();c.moveTo(x+r,y);c.arcTo(x+w,y,x+w,y+h,r);c.arcTo(x+w,y+h,x,y+h,r);c.arcTo(x,y+h,x,y,r);c.arcTo(x,y,x+w,y,r);c.closePath()}
  function bolt(c,x1,y1,x2,y2,w){c.beginPath();c.moveTo(x1,y1);const n=8;for(let i=1;i<n;i++){const t=i/n;c.lineTo(x1+(x2-x1)*t+(Math.random()-.5)*40,y1+(y2-y1)*t+(Math.random()-.5)*40)}c.lineTo(x2,y2);c.lineWidth=w;c.stroke()}

  async function open(o){
    o=Object.assign({rar:0,sh:0,kind:'P',name:'?',sub:'',img:'',col:'',base:'cardpack/'},o||{});const R=Math.max(0,Math.min(6,o.rar|0));
    let art=null;const artP=o.img?loadImg(o.img,8000).then(v=>art=v):null;const pk=await loadImg(o.base+'pk'+Math.min(3,R)+'.webp',800);let frm=null;loadImg(o.base+'frame'+R+'.webp',4000).then(v=>frm=v);
    return new Promise(done=>{
      const cv=document.createElement('canvas');cv.style.cssText='position:fixed;inset:0;width:100vw;height:100vh;z-index:99999;touch-action:none;background:#000';document.body.appendChild(cv);
      const c=cv.getContext('2d');let W,H,DP;function rs(){DP=Math.min(2,devicePixelRatio||1);W=innerWidth;H=innerHeight;cv.width=W*DP;cv.height=H*DP;c.setTransform(DP,0,0,DP,0,0)}rs();addEventListener('resize',rs);
      let rev=0,ph='in',t0=performance.now(),pt=0,taps=0,shake=0,flash=0,P=[],bolts=0,closed=false;
      const C=COL[R],C2=COL2[R];
      function burst(n,sp,col,life){for(let i=0;i<n;i++){const a=Math.random()*Math.PI*2,v=sp*(.3+Math.random());P.push({x:W/2,y:H*.48,vx:Math.cos(a)*v,vy:Math.sin(a)*v-(R>=3?2:0),l:life*(.6+Math.random()*.6),L:life,s:2+Math.random()*4*(1+R*.4),c:col||C,k:Math.random()<.3?'star':'dot',conf:R>=3&&Math.random()<.5})}}
      function setPh(p){ph=p;pt=performance.now()}
      cv.onpointerdown=()=>{
        if(ph==='in'||ph==='idle'){taps++;S.tap(taps);shake=6+taps*4+R*3;flash=.15*taps;burst(6+taps*4,3+taps,C,40);
          if(taps>=TAPS[R]){S.tear();setPh(R>=3?'pillar':'tear');if(R>=3)S.boom();shake=R>=2?22:10}}
        else if(ph==='show'&&performance.now()-pt>900){close()}};
      function close(){if(closed)return;closed=true;removeEventListener('resize',rs);cv.style.transition='opacity .35s';cv.style.opacity='0';setTimeout(()=>{cv.remove();done()},360)}
      // 카드 그리기
      function card(x,y,w,h,now,sx){
        c.save();c.translate(x,y);c.scale(sx,1);c.translate(-w/2,-h/2);
        const back=sx<0;
        // 테두리
        const g=c.createLinearGradient(0,0,w,h);if(o.sh){for(let i=0;i<=6;i++)g.addColorStop(i/6,'hsl('+((now/8+i*60)%360)+',90%,62%)')}else{g.addColorStop(0,C);g.addColorStop(.5,'#fff');g.addColorStop(1,C2)}
        rr(c,0,0,w,h,w*.07);c.fillStyle=g;c.fill();
        if(back){rr(c,w*.05,h*.04,w*.9,h*.92,w*.05);c.fillStyle=C2;c.fill();c.fillStyle='rgba(255,255,255,.9)';c.font=`900 ${w*.22}px sans-serif`;c.textAlign='center';c.textBaseline='middle';c.fillText('?',w/2,h/2);c.restore();return}
        rr(c,w*.05,h*.04,w*.9,h*.92,w*.05);const ig=c.createLinearGradient(0,0,0,h);ig.addColorStop(0,'#fff');ig.addColorStop(1,o.col||'#dfe8f0');c.fillStyle=ig;c.fill();
        // 그림 칸
        const ax=w*.1,ay=h*.17,aw=w*.8,ah=h*.5;rr(c,ax,ay,aw,ah,w*.03);const bg=c.createRadialGradient(w/2,ay+ah/2,4,w/2,ay+ah/2,aw*.7);bg.addColorStop(0,'#fff');bg.addColorStop(1,o.col||C);c.fillStyle=bg;c.fill();
        c.save();rr(c,ax,ay,aw,ah,w*.03);c.clip();
        if(R>=1){c.globalAlpha=.25;c.fillStyle='#fff';for(let i=0;i<12;i++){c.save();c.translate(w/2,ay+ah/2);c.rotate(now/2000*(R)+i*Math.PI/6);c.beginPath();c.moveTo(0,0);c.lineTo(aw,-aw*.13);c.lineTo(aw,aw*.13);c.fill();c.restore()}c.globalAlpha=1}
        if(art){c.imageSmoothingEnabled=false;const s=Math.min(aw,ah)*.86;const bob=Math.sin(now/300)*s*.03;c.drawImage(art,w/2-s/2,ay+ah/2-s/2+bob,s,s)}
        else{c.fillStyle='rgba(0,0,0,.7)';c.font=`900 ${w*.3}px sans-serif`;c.textAlign='center';c.fillText(o.kind==='M'?'📀':'?',w/2,ay+ah/2)}
        // 홀로 반짝임
        if(R>=1||o.sh){const sx2=((now/(R>=3?6:10))%(aw*2.4))-aw*.6;const hg=c.createLinearGradient(ax+sx2,ay,ax+sx2+aw*.5,ay+ah);hg.addColorStop(0,'rgba(255,255,255,0)');hg.addColorStop(.5,'rgba(255,255,255,.55)');hg.addColorStop(1,'rgba(255,255,255,0)');c.fillStyle=hg;c.fillRect(ax,ay,aw,ah)}
        c.restore();
        c.fillStyle=C2;rr(c,w*.1,h*.7,w*.8,h*.07,h*.035);c.fill();c.fillStyle='#fff';c.font=`900 ${w*.065}px sans-serif`;c.textAlign='center';c.fillText((o.sh?'✨ 이로치 · ':'')+RN[R]+(o.kind==='M'?' 기술 카드':' 포켓몬'),w/2,h*.735);
        c.fillStyle='#333';c.font=`800 ${w*.07}px sans-serif`;c.fillText(o.sub||'',w/2,h*.84,w*.8);
        if(frm){c.shadowBlur=0;c.drawImage(frm,-w*.07,-h*.055,w*1.14,h*1.11)}
        // 이름 (프레임 위에 띠로)
        c.fillStyle='rgba(20,22,40,.82)';rr(c,w*.12,h*.065,w*.76,h*.075,h*.03);c.fill();c.fillStyle='#fff';c.font=`900 ${w*.085}px sans-serif`;c.textAlign='left';c.textBaseline='middle';c.fillText(o.name,w*.16,h*.103,R>=4?w*.46:w*.5);
        c.textAlign='right';c.fillStyle=R>=4?COL[R]:R>=3?'#ffd060':R>=2?'#e0a0ff':R>=1?'#8fd0ff':'#ccc';c.font=`900 ${w*.065}px sans-serif`;c.fillText(R>=4?['🔥','🌌','👑'][R-4]+'★'+(R+1):'★'.repeat(R+1),w*.85,h*.103,w*.22);
        c.restore()}
      function frame(now){
        if(closed)return;const e=now-pt;c.save();
        const sk=shake*(Math.random()-.5);const sk2=shake*(Math.random()-.5);shake*=.88;c.translate(sk,sk2);
        // 배경
        const dark=ph==='pillar'||(R>=3&&ph==='tear');const bg=c.createRadialGradient(W/2,H*.48,10,W/2,H*.48,Math.max(W,H)*.8);bg.addColorStop(0,dark?'#2a1a00':C2+'cc');bg.addColorStop(1,'#05050c');c.fillStyle=bg;c.fillRect(-30,-30,W+60,H+60);
        const ph_=Math.min(W*.62,H*.5)/280,pw=210*ph_,phh=280*ph_;
        if(ph==='in'||ph==='idle'){if(ph==='in'&&e>600)setPh('idle');const k=ph==='in'?Math.min(1,e/600):1;const ease=1-Math.pow(1-k,3);
          const y=H*.48+(1-ease)*H*.6+Math.sin(now/400)*6;const rot=Math.sin(now/600)*.05+(taps?(Math.random()-.5)*.04*taps:0);
          c.save();c.translate(W/2,y);c.rotate(rot);c.shadowColor=C;c.shadowBlur=20+taps*15+R*8;if(pk)c.drawImage(pk,-pw/2,-phh/2,pw,phh);else{c.fillStyle=C;c.fillRect(-pw/2,-phh/2,pw,phh)}c.restore();
          if(taps&&R>=2){c.strokeStyle='rgba(255,255,255,'+(.5+taps*.15)+')';c.lineWidth=2;c.beginPath();for(let i=0;i<taps*3;i++){const a=i*2.4;c.moveTo(W/2,H*.48);c.lineTo(W/2+Math.cos(a)*pw*.45,H*.48+Math.sin(a)*phh*.4)}c.stroke()}
          c.fillStyle='#fff';c.font='900 22px sans-serif';c.textAlign='center';c.globalAlpha=.6+.4*Math.sin(now/200);c.fillText(TAPS[R]>1?'톡톡 눌러서 뜯기! ('+(TAPS[R]-taps)+')':'눌러서 뜯기!',W/2,H*.88);c.globalAlpha=1}
        if(ph==='pillar'){const k=Math.min(1,e/900);const bw=W*.08+k*W*.3;const g=c.createLinearGradient(W/2-bw/2,0,W/2+bw/2,0);g.addColorStop(0,'rgba(255,176,32,0)');g.addColorStop(.5,'rgba(255,240,180,'+(.6+.4*k)+')');g.addColorStop(1,'rgba(255,176,32,0)');c.fillStyle=g;c.fillRect(W/2-bw/2,-30,bw,H+60);
          c.save();c.translate(W/2,H*.48);c.shadowColor='#ffd060';c.shadowBlur=50;if(pk)c.drawImage(pk,-pw/2,-phh/2,pw,phh);c.restore();if(Math.random()<.3){burst(4,6,'#ffe08a',50)}shake=Math.max(shake,4);if(e>1000){setPh('tear');S.tear()}}
        if(ph==='tear'){const k=Math.min(1,e/500);const cut=.2;
          if(pk){c.save();c.translate(W/2+k*pw*.6,H*.48-phh/2-k*H*.5);c.rotate(k*1.2);c.drawImage(pk,0,0,pk.width,pk.height*cut,-pw/2,0,pw,phh*cut);c.restore();
            c.save();c.globalAlpha=1-k;c.translate(W/2,H*.48+k*H*.6);c.drawImage(pk,0,pk.height*cut,pk.width,pk.height*(1-cut),-pw/2,-phh/2+phh*cut,pw,phh*(1-cut));c.restore()}
          if(e>250&&!rev){rev=1;flash=1;S.rev(R);burst(30+Math.min(R,3)*40,5+Math.min(R,3)*2,C,70);if(o.sh)burst(40,7,'#fff',80);if(R>=2)bolts=1}
          if(e>500)setPh('card')}
        if(ph==='card'||ph==='show'){const k=Math.min(1,e/(R>=3?1400:R>=2?1000:600));const ease=1-Math.pow(1-k,3);
          // 빛줄기
          c.save();c.translate(W/2,H*.48);c.rotate(now/(R>=3?900:2000));c.globalAlpha=.25+R*.08;c.fillStyle=R>=3?'#ffe08a':C;for(let i=0;i<(8+R*4);i++){c.rotate(Math.PI*2/(8+R*4));c.beginPath();c.moveTo(0,0);c.lineTo(Math.max(W,H),-40-R*15);c.lineTo(Math.max(W,H),40+R*15);c.fill()}c.restore();c.globalAlpha=1;
          if(bolts&&(ph==='card'||Math.random()<.04)){c.strokeStyle='rgba(220,180,255,.9)';c.shadowColor='#c070ff';c.shadowBlur=18;for(let i=0;i<2;i++){const a=Math.random()*Math.PI*2;bolt(c,W/2,H*.48,W/2+Math.cos(a)*W*.6,H*.48+Math.sin(a)*H*.5,3)}c.shadowBlur=0;if(Math.random()<.3)S.zap()}
          const cw=Math.min(W*.7,H*.42*.72),chh=cw/.72;let sx=1,y=H*.48+(1-ease)*H*.2,sc=.3+.7*ease;
          if(R==2)sx=Math.cos((1-ease)*Math.PI);if(R>=3)sx=Math.cos((1-ease)*Math.PI*4);
          if(R>=1){c.save();c.translate(W/2,y);c.scale(sc,sc);const pul=.6+.4*Math.sin(now/(R>=3?160:260));
            const ag=c.createRadialGradient(0,0,cw*.3,0,0,cw*(1+R*.25));const AC=R>=6?'hsla('+((now/4)%360)+',100%,65%,':R===5?'rgba(40,240,220,':R===4?'rgba(255,50,90,':R>=3?'rgba(255,200,60,':R>=2?'rgba(190,90,255,':'rgba(70,160,255,';ag.addColorStop(0,AC+(.55*pul)+')');ag.addColorStop(1,AC+'0)');c.fillStyle=ag;c.fillRect(-cw*2,-chh*2,cw*4,chh*4);
            const n=R*10;for(let i=0;i<n;i++){const a=now/(900-R*150)*(i%2?1:-1)+i*Math.PI*2/n,rx=cw*(.62+.06*Math.sin(now/300+i)),ry=chh*(.6+.05*Math.cos(now/280+i));c.fillStyle=R>=6||R===3?'hsl('+((i*36+now/5)%360)+',100%,70%)':R===5?'#aafff4':R===4?'#ff9ab0':R>=2?'#e2b8ff':'#bfe0ff';c.globalAlpha=.5+.5*Math.sin(now/120+i);c.beginPath();c.arc(Math.cos(a)*rx,Math.sin(a)*ry,2+R*1.2,0,7);c.fill()}
            if(R>=2){c.globalAlpha=.7*pul;c.lineWidth=3+R;c.strokeStyle=R>=6||R===3?'hsl('+((now/4)%360)+',100%,65%)':R===5?'#22e6d0':R===4?'#ff3b6b':'#c070ff';c.shadowColor=c.strokeStyle;c.shadowBlur=25;rr(c,-cw*.58,-chh*.56,cw*1.16,chh*1.12,cw*.1);c.stroke()}c.restore()}
          c.save();c.translate(W/2,y);c.scale(sc,sc);c.shadowColor=o.sh?'#fff':R>=3?'#ffd060':C;c.shadowBlur=30+R*25;card(0,0,cw,chh,now,sx||.001);c.restore();
          if(ph==='card'&&k>=1){setPh('show');if(R>=1)burst(20+Math.min(R,3)*20,4+Math.min(R,3),C,60)}
          if(ph==='show'){if(R>=3&&Math.random()<.5)P.push({x:Math.random()*W,y:-10,vx:(Math.random()-.5)*2,vy:2+Math.random()*3,l:200,L:200,s:4+Math.random()*4,c:'hsl('+(Math.random()*360|0)+',90%,60%)',k:'conf'});
            if(R>=1&&Math.random()<.2+R*.1)P.push({x:W/2+(Math.random()-.5)*cw,y:H*.48+(Math.random()-.5)*chh,vx:0,vy:-.3,l:40,L:40,s:3+Math.random()*3,c:'#fff',k:'star'});
            if(e>900){c.fillStyle='#fff';c.font='900 20px sans-serif';c.textAlign='center';c.globalAlpha=.6+.4*Math.sin(now/200);c.fillText('눌러서 가방에 넣기',W/2,H*.92);c.globalAlpha=1}}}
        // 파티클
        for(let i=P.length-1;i>=0;i--){const p=P[i];p.x+=p.vx;p.y+=p.vy;if(p.k==='conf'){p.vx+=Math.sin(now/200+i)*.05}else{p.vx*=.96;p.vy=p.vy*.96+.05}p.l--;if(p.l<=0||p.y>H+20){P.splice(i,1);continue}
          c.globalAlpha=Math.min(1,p.l/p.L*1.5);c.fillStyle=p.c;if(p.k==='star'){c.save();c.translate(p.x,p.y);c.rotate(now/300);c.beginPath();for(let j=0;j<8;j++){const r=j%2?p.s*.35:p.s*1.4;c.lineTo(Math.cos(j*Math.PI/4)*r,Math.sin(j*Math.PI/4)*r)}c.fill();c.restore()}else if(p.k==='conf'){c.save();c.translate(p.x,p.y);c.rotate(now/150+i);c.fillRect(-p.s/2,-p.s/4,p.s,p.s/2);c.restore()}else{c.beginPath();c.arc(p.x,p.y,p.s,0,7);c.fill()}}c.globalAlpha=1;
        c.restore();
        if(flash>0){c.fillStyle=R>=3?'rgba(255,240,200,'+flash+')':'rgba(255,255,255,'+flash+')';c.fillRect(0,0,W,H);flash=Math.max(0,flash-.04)}
        requestAnimationFrame(frame)}
      requestAnimationFrame(frame)})}
  return{open}})();
if(typeof window!=='undefined')window.CardPack=CardPack;
