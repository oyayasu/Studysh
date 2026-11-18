// FSRS-6 equations and defaults ported from Android StudyFsrs.kt.
// Keys and JSON state are identical to the Android edition.
export const FSRS_WEIGHTS = [0.212,1.2931,2.3065,8.2956,6.4133,0.8334,3.0194,0.001,1.8722,
  0.1666,0.796,1.4835,0.0614,0.2629,1.6483,0.6014,1.8729,0.5425,0.0912,0.0658,0.1542];
const W=FSRS_WEIGHTS, DAY=86400000, RETENTION=0.9, DECAY=-W[20];
const FACTOR=RETENTION**(1/DECAY)-1;
const clamp=(x,min,max)=>Math.max(min,Math.min(max,x));
function initialDifficulty(g){return clamp(W[4]-Math.exp(W[5]*(g-1))+1,1,10);}
function difficulty(d,g){const delta=-W[6]*(g-3)*(10-d)/9;return clamp(W[7]*initialDifficulty(4)+(1-W[7])*(d+delta),1,10);}
function recall(t,s){return clamp((1+FACTOR*t/s)**DECAY,0,1);}
function interval(s){return clamp(Math.round(s/FACTOR*(RETENTION**(1/DECAY)-1)),1,36500)*DAY;}
function fresh(g){return [clamp(W[g-1],0.1,36500),initialDifficulty(g)];}
function updated(s,d,t,g){
 const nextD=difficulty(d,g),r=recall(t,s);
 let nextS;
 if(t<1){
  const candidate=s*Math.exp(W[17]*(g-3+W[18]))*s**(-W[19]);
  nextS=g>=2?Math.max(s,candidate):candidate;
 } else if(g===1){
  nextS=Math.min(s,W[11]*nextD**(-W[12])*((s+1)**W[13]-1)*Math.exp((1-r)*W[14]));
 } else {
  const mult=Math.exp(W[8])*(11-nextD)*s**(-W[9])*(Math.exp((1-r)*W[10])-1)
    *(g===2?W[15]:1)*(g===4?W[16]:1);
  nextS=s*(1+mult);
 }
 return [clamp(nextS,0.001,36500),nextD];
}
export function dueAt(card){return Number(card?.dueAt)||0;}
export function retention(card,now=Date.now()){
 if(!card||card.state!=="review"||!(card.stability>0))return null;
 return recall(Math.max(0,now-(card.lastAt||now))/DAY,card.stability);
}
export function migrateLegacy(previous,now=Date.now()){
 if(!previous)return null;
 if(previous.algorithm==="fsrs-6" || !(previous.attempts>0))return previous;
 const grade=(previous.streak||0)===0?1:(previous.streak>=3?4:3);
 const [stability,diff]=fresh(grade);
 return {...previous,algorithm:"fsrs-6",state:(previous.streak||0)===0?"relearning":"review",
  step:0,stability,difficulty:diff,dueAt:Math.max(0,Number(previous.dueAt)||now),
  reviews:previous.reviews||[],migration:"legacy-estimate"};
}
export function review(previous,rating,now=Date.now()){
 if(!Number.isInteger(rating)||rating<1||rating>4)throw Error("Rating must be 1–4");
 const card=previous?structuredClone(previous):{},state=card.state||"new",last=Number(card.lastAt)||0;
 const elapsed=last>0?Math.max(0,now-last)/DAY:0;
 const oldS=Number(card.stability)||0,oldD=Number(card.difficulty)||0;
 const hasMemory=oldS>0&&oldD>=1&&oldD<=10;
 let nextState="review",step=0,stability=oldS,diff=oldD,due=now+DAY;
 const isLearning=state==="learning"||state==="relearning",wasRelearning=state==="relearning";
 if(state==="new"||(!hasMemory&&!isLearning)){
  [stability,diff]=fresh(rating);
  if(rating===1){nextState="learning";due=now+60000;}
  else if(rating===2){nextState="learning";due=now+600000;}
  else if(rating===3){nextState="learning";step=1;due=now+600000;}
  else due=now+interval(stability);
 } else if(isLearning){
  [stability,diff]=hasMemory?updated(oldS,oldD,elapsed,rating):fresh(rating);
  const oldStep=Number(card.step)||0;
  if(rating===1){nextState=wasRelearning?"relearning":"learning";due=now+60000;}
  else if(rating===2){nextState=wasRelearning?"relearning":"learning";step=oldStep;due=now+600000;}
  else if(rating===3 && oldStep===0 && !wasRelearning){nextState="learning";step=1;due=now+600000;}
  else due=now+interval(stability);
 } else {
  [stability,diff]=updated(oldS,oldD,elapsed,rating);
  if(rating===1){nextState="relearning";due=now+600000;}
  else due=now+interval(stability);
 }
 if(!Number.isFinite(stability)||stability<=0||!Number.isFinite(diff)){
  [stability,diff]=fresh(rating);due=now+interval(stability);nextState="review";step=0;
 }
 const logs=Array.isArray(card.reviews)?card.reviews:[];
 logs.push({at:now,rating,elapsedDays:elapsed,stability,difficulty:diff,dueAt:due,state:nextState});
 return {...card,algorithm:"fsrs-6",state:nextState,step,stability,difficulty:diff,lastAt:now,
  dueAt:due,reviews:logs,attempts:(card.attempts||0)+1,correct:(card.correct||0)+(rating===1?0:1)};
}
