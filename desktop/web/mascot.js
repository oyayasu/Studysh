// Resolution-independent vector animation (CSS transitions + continuous keyframes).
// No GIF, sprite sheet or network asset is required.
export function mascot(mood="idle") {
 const safe=["idle","happy","sad"].includes(mood)?mood:"idle";
 return '<div class="mascot '+safe+'" role="img" aria-label="Studysh mascot '+safe+'">'+
 '<svg viewBox="0 0 240 240" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'+
 '<defs><radialGradient id="body-gradient" cx="30%" cy="22%" r="86%"><stop stop-color="#f8e9ff"/><stop offset=".52" stop-color="#d8b7ee"/><stop offset="1" stop-color="#ab82d2"/></radialGradient></defs>'+
 '<ellipse class="shadow" cx="120" cy="219" rx="74" ry="8" fill="#a590bd" opacity=".17"/>'+
 '<g class="body">'+
 '<ellipse class="left-foot" cx="82" cy="205" rx="27" ry="13" fill="#a37bcc"/><ellipse class="right-foot" cx="160" cy="205" rx="27" ry="13" fill="#a37bcc"/>'+
 '<ellipse cx="105" cy="42" rx="19" ry="28" fill="#dbbdf2" transform="rotate(-17 105 42)"/>'+
 '<ellipse cx="139" cy="42" rx="18" ry="27" fill="#dbbdf2" transform="rotate(16 139 42)"/>'+
 '<circle cx="120" cy="124" r="88" fill="url(#body-gradient)"/>'+
 '<ellipse cx="92" cy="59" rx="35" ry="12" fill="#fff" opacity=".34"/>'+
 '<ellipse class="left-paw" cx="39" cy="154" rx="19" ry="28" fill="#d5b3eb"/>'+
 '<ellipse class="right-paw" cx="201" cy="154" rx="19" ry="28" fill="#d5b3eb"/>'+
 '<g class="open-eye"><ellipse cx="94" cy="126" rx="9" ry="15" fill="#47334f"/><ellipse cx="147" cy="126" rx="9" ry="15" fill="#47334f"/>'+
 '<circle cx="91" cy="120" r="3.7" fill="#fff"/><circle cx="144" cy="120" r="3.7" fill="#fff"/></g>'+
 '<g class="closed-eye" fill="none" stroke="#47334f" stroke-width="5" stroke-linecap="round"><path d="M80 128 Q94 110 106 129"/><path d="M134 129 Q147 110 160 128"/></g>'+
 '<ellipse cx="70" cy="151" rx="17" ry="8" fill="#ee9ec8" opacity=".52"/><ellipse cx="171" cy="151" rx="17" ry="8" fill="#ee9ec8" opacity=".52"/>'+
 '<g fill="none" stroke="#e98fb7" opacity=".63" stroke-width="2.6"><path d="M65 147l-4 8m12-8-4 8m99-8-4 8m12-8-4 8"/></g>'+
 '<path class="default-mouth" d="M105 158 Q108 179 120 162 Q132 179 136 158" fill="none" stroke="#48314e" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>'+
 '<path class="smile" d="M105 158 Q108 179 120 163 Q132 179 136 158" fill="none" stroke="#48314e" stroke-width="5" stroke-linecap="round"/>'+
 '<g class="brow" fill="none" stroke="#47334f" stroke-width="4" stroke-linecap="round"><path d="M84 101 Q94 93 102 100"/><path d="M138 100 Q147 93 157 101"/></g>'+
 '<g class="sad-mouth"><ellipse cx="121" cy="167" rx="11" ry="10" fill="#4a3551"/><ellipse cx="122" cy="173" rx="7" ry="4" fill="#f3a0bb"/></g>'+
 '<g class="tear" fill="#98d6fa"><path d="M94 138 Q80 157 85 173 Q93 180 101 174 L101 138Z"/><path d="M148 138 Q161 157 157 173 Q149 180 142 174 L142 138Z"/></g>'+
 '<g class="tear" fill="#b1e7ff" opacity=".85"><ellipse cx="91" cy="151" rx="9" ry="5"/><ellipse cx="151" cy="151" rx="9" ry="5"/></g>'+
 '</g><g class="sparkle" fill="#e2b467"><path d="M32 85l4 10 10 4-10 4-4 10-4-10-10-4 10-4zm176 9l3 8 8 3-8 3-3 8-3-8-8-3 8-3z"/></g>'+
 '</svg></div>';
}
