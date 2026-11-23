// Renders the listing artwork in marketing/images/ from real in-game captures (marketing/images/screenshots/) and
// the bundled OFL fonts. Usage (from the repository root): node marketing/source/render.cjs [name...]
//
// Uses the `playwright` package with Chromium when it is installed (NODE_PATH="$(npm root -g)" if installed
// globally). Without it, falls back to a headless Edge or Chrome started from the command line, which needs
// nothing installed beyond the browser itself.
const path = require('path');
const fs = require('fs');
const { pathToFileURL } = require('url');
const { spawnSync } = require('child_process');

// The release and what it runs on. A new Minecraft version or loader is one line here
// (plus the platform line in curseforge/description.md).
const RELEASE = { version: '1.0', name: 'An environmental realism engine' };
const VERSIONS = [
  { minecraft: '1.20.1', loaders: ['Fabric', 'Forge'] },
  { minecraft: '1.21.1', loaders: ['Fabric', 'NeoForge'] },
  { minecraft: '26.2', loaders: ['Fabric', 'NeoForge'] },
  { minecraft: '26.3', loaders: ['Fabric', 'NeoForge'] },
];
const LOADERS = ['Fabric', 'Forge', 'NeoForge'].filter((loader) => VERSIONS.some((v) => v.loaders.includes(loader)));

// The palette is a sky through a day and a year: night and dusk behind everything, then one colour per subject.
const C = {
  night: '#0a1224', dusk: '#182748', paper: '#f4f1e8', haze: '#c8d4e6',
  sun: '#f7b955', water: '#3ccbd9', leaf: '#8fcf6b', autumn: '#ee7d3d', snow: '#bfe6f7', aurora: '#62f0b4', storm: '#a5a1f7',
};

const root = path.resolve(__dirname, '..', '..');
const out = path.join(root, 'marketing', 'images');
const shots = path.join(out, 'screenshots');
const fonts = path.join(__dirname, 'fonts');
const url = (file) => pathToFileURL(file).href;
const shot = (name) => url(path.join(shots, name));

const css = `
@font-face { font-family: Display; font-weight: 700; src: url('${url(path.join(fonts, 'playfair-700.woff2'))}'); }
@font-face { font-family: Display; font-weight: 900; src: url('${url(path.join(fonts, 'playfair-900.woff2'))}'); }
@font-face { font-family: Display; font-weight: 900; font-style: italic; src: url('${url(path.join(fonts, 'playfair-900-italic.woff2'))}'); }
@font-face { font-family: Body; font-weight: 500; src: url('${url(path.join(fonts, 'barlow-500.woff2'))}'); }
@font-face { font-family: Body; font-weight: 700; src: url('${url(path.join(fonts, 'barlow-700.woff2'))}'); }
@font-face { font-family: Body; font-weight: 800; src: url('${url(path.join(fonts, 'barlow-800.woff2'))}'); }
@font-face { font-family: Cond; font-weight: 700; src: url('${url(path.join(fonts, 'barlow-condensed-700.woff2'))}'); }
* { box-sizing: border-box; }
html, body { margin: 0; overflow: hidden; background: ${C.night}; color: ${C.paper}; font-family: Body, sans-serif; font-weight: 700; }
.stage { position: relative; overflow: hidden;
         background: radial-gradient(ellipse at 84% -18%, rgba(247,185,85,.26) 0%, rgba(247,185,85,0) 46%),
                     linear-gradient(172deg, ${C.dusk} 0%, ${C.night} 70%); }
.display { font-family: Display, serif; font-weight: 900; letter-spacing: -.005em; }
.title { background: linear-gradient(180deg, #ffffff 0%, #fdeccb 58%, ${C.sun} 108%);
         -webkit-background-clip: text; background-clip: text; color: transparent; }
.title i { font-style: italic; }
.shadow { filter: drop-shadow(0 4px 0 rgba(4,9,20,.9)) drop-shadow(0 0 30px rgba(247,185,85,.2)); }
.kicker { font-family: Cond, sans-serif; color: ${C.sun}; letter-spacing: .24em; font-size: 24px; text-transform: uppercase; }
/* Isobars: the rings of a weather map, centred on the glow. */
.isobars { position: absolute; inset: 0; opacity: .5;
           background: repeating-radial-gradient(circle at 84% -18%, rgba(255,255,255,0) 0 58px, rgba(255,255,255,.07) 58px 60px); }
.backdrop { position: absolute; inset: -40px; background-size: cover; background-position: center;
            filter: blur(16px) brightness(.26) saturate(1.2); }
.frame { position: relative; overflow: hidden; background: #000; border-radius: 14px; border: 4px solid rgba(244,241,232,.94);
         box-shadow: 0 0 0 3px rgba(4,9,20,.9), 0 26px 60px rgba(0,0,0,.6); }
.frame img.fill { display: block; width: 100%; height: 100%; object-fit: cover; }
/* The horizon: one line through the colours of a day. */
.horizon { height: 5px; border-radius: 3px;
           background: linear-gradient(90deg, ${C.sun} 0%, ${C.autumn} 30%, ${C.storm} 62%, ${C.water} 100%); }
.label { position: absolute; display: inline-flex; align-items: center; gap: 9px; padding: 6px 15px 5px; border-radius: 999px;
         font-family: Cond, sans-serif; font-size: 18px; letter-spacing: .1em; text-transform: uppercase; color: ${C.night};
         background: ${C.paper}; box-shadow: 0 6px 18px rgba(0,0,0,.45); white-space: nowrap; }
.dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; }
ul.points { list-style: none; padding: 0; margin: 0; }
ul.points li { position: relative; padding-left: 36px; margin: 0 0 15px; font-size: 25px; line-height: 1.3; color: #e4e9f2; font-weight: 500; }
ul.points li::before { content: ''; position: absolute; left: 2px; top: 7px; width: 17px; height: 17px; border-radius: 50%;
                       background: radial-gradient(circle at 50% 50%, var(--accent, ${C.sun}) 0 42%, rgba(10,18,36,.9) 44% 60%, var(--accent, ${C.sun}) 62%); }
b.hi { color: #fff; font-weight: 800; }
code { font-family: 'Cascadia Mono', Consolas, 'DejaVu Sans Mono', monospace; font-size: .86em; color: #fff;
       background: rgba(255,255,255,.1); border-radius: 6px; padding: 1px 7px 2px; white-space: nowrap; }
.stats { display: flex; border-radius: 14px; overflow: hidden; background: rgba(10,18,36,.9);
         border: 3px solid rgba(200,212,230,.26); box-shadow: 0 18px 50px rgba(0,0,0,.5); }
.stats > div { flex: 1; padding: 12px 20px 10px; border-left: 3px solid rgba(200,212,230,.14); }
.stats > div:first-child { border-left: 0; }
.stats .t { font-family: Cond, sans-serif; font-size: 16px; letter-spacing: .16em; color: #aebbd0; text-transform: uppercase; }
.stats .v { font-family: Display, serif; font-weight: 900; font-size: 31px; color: ${C.paper}; margin-top: 2px; white-space: nowrap; }
.pill { display: inline-block; border-radius: 999px; padding: 6px 20px 5px; margin-right: 10px; font-family: Cond, sans-serif;
        font-size: 23px; letter-spacing: .08em; color: ${C.night}; background: ${C.sun}; }
`;

const page = (width, height, body, transparent = false) => ({
  width, height, transparent,
  html: `<!doctype html><html><head><meta charset="utf-8"><style>${css}
body { width: ${width}px; height: ${height}px; } .stage { width: ${width}px; height: ${height}px; }
${transparent ? 'html, body, .stage { background: transparent; }' : ''}</style></head>
<body><div class="stage">${body}</div></body></html>`,
});

// A capture shown in a frame of the given size; `pos` is the CSS object-position (which part stays in view).
// `zoom` enlarges the capture about that same point (a crop: for pictures whose subject is small, like stars).
const framed = (file, w, h, pos = 'center', style = '', zoom = 1) =>
  `<div class="frame" style="width:${w}px;height:${h}px;${style}"><img class="fill" src="${shot(file)}" style="object-position:${pos}${zoom !== 1 ? `;transform:scale(${zoom});transform-origin:${pos}` : ''}"></div>`;
const label = (text, color, pos) => `<span class="label" style="${pos}"><span class="dot" style="background:${color}"></span>${text}</span>`;
const stats = (items) => `<div class="stats">${items.map(([t, v, flex]) =>
  `<div${flex ? ` style="flex:${flex}"` : ''}><div class="t">${t}</div><div class="v">${v}</div></div>`).join('')}</div>`;
// Four captures in a two-by-two grid, each with a label.
const grid = (items, w = 462, h = 260) => `<div style="display:grid;grid-template-columns:${w}px ${w}px;gap:16px">
  ${items.map(([file, text, color, pos, zoom]) => `<div style="position:relative">${framed(file, w, h, pos || 'center', `border-color:${color}`, zoom || 1)}
    ${label(text, color, 'left:14px;top:14px')}</div>`).join('')}</div>`;

// A feature card: copy on the left, artwork on the right.
const card = ({ kicker, title, points, art, width = 1600, height = 900, artWidth = 940, backdrop, color = C.sun }) => page(width, height, `
  <div class="backdrop" style="background-image:url('${shot(backdrop)}')"></div><div class="isobars"></div>
  <div style="position:absolute;left:74px;top:0;bottom:0;width:${width - artWidth - 146}px;display:flex;flex-direction:column;justify-content:center;--accent:${color}">
    <div class="kicker" style="color:${color}">${kicker}</div>
    <div class="display title shadow" style="font-size:66px;line-height:1.05;margin:12px 0 22px">${title}</div>
    <div class="horizon" style="width:220px;margin-bottom:30px"></div>
    <ul class="points">${points.map((point) => `<li>${point.replace(/ ([^\s<>"=]+(?:<\/\w+>)?)$/, '&nbsp;$1')}</li>`).join('')}</ul>
  </div>
  <div style="position:absolute;right:62px;top:50%;transform:translateY(-50%);width:${artWidth}px">${art}</div>`);

const versionText = VERSIONS.map((v) => v.minecraft).join(' · ');

const header = (title, color = C.sun) => page(1400, 120, `
  <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:24px">
    <div class="horizon" style="width:230px;background:linear-gradient(90deg, rgba(255,255,255,0), ${color})"></div>
    <span class="dot" style="width:18px;height:18px;background:radial-gradient(circle,${color} 0 42%,rgba(10,18,36,.95) 44% 60%,${color} 62%)"></span>
    <div class="display title shadow" style="font-size:58px;white-space:nowrap">${title}</div>
    <span class="dot" style="width:18px;height:18px;background:radial-gradient(circle,${color} 0 42%,rgba(10,18,36,.95) 44% 60%,${color} 62%)"></span>
    <div class="horizon" style="width:230px;background:linear-gradient(90deg, ${color}, rgba(255,255,255,0))"></div>
  </div>`, true);

const divider = () => page(1400, 44, `
  <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:16px">
    <div class="horizon" style="width:420px;background:linear-gradient(90deg, rgba(255,255,255,0), ${C.sun})"></div>
    ${[C.sun, C.autumn, C.storm, C.water, C.aurora].map((c) => `<span class="dot" style="width:15px;height:15px;background:radial-gradient(circle,${c} 0 42%,rgba(10,18,36,.95) 44% 60%,${c} 62%)"></span>`).join('')}
    <div class="horizon" style="width:420px;background:linear-gradient(90deg, ${C.aurora}, rgba(255,255,255,0))"></div>
  </div>`, true);

// The mod's icon, redrawn at listing size: sun behind a snow-capped peak, a rain cloud, tracks across the snow.
const iconArt = `
<svg viewBox="0 0 512 512" width="512" height="512" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <clipPath id="tile"><rect width="512" height="512" rx="104"/></clipPath>
    <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#16264f"/><stop offset=".5" stop-color="#3f6fb5"/><stop offset=".8" stop-color="#f5b56a"/>
    </linearGradient>
    <radialGradient id="glow" cx=".2" cy=".66" r=".45"><stop offset="0" stop-color="#ffe7a8" stop-opacity=".9"/><stop offset="1" stop-color="#ffe7a8" stop-opacity="0"/></radialGradient>
    <linearGradient id="rock" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7d8fb8"/><stop offset="1" stop-color="#3d4a74"/></linearGradient>
    <linearGradient id="snow" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#d4e6f7"/></linearGradient>
  </defs>
  <g clip-path="url(#tile)">
    <rect width="512" height="512" fill="url(#sky)"/>
    <rect width="512" height="512" fill="url(#glow)"/>
    <circle cx="104" cy="318" r="62" fill="#ffd36b"/><circle cx="104" cy="318" r="44" fill="#fff0bd"/>
    <path d="M96 420 L262 118 L428 420 Z" fill="url(#rock)"/>
    <path d="M262 118 L330 242 L296 222 L268 262 L238 220 L204 244 Z" fill="#ffffff"/>
    <path d="M262 118 L428 420 L262 420 Z" fill="#1c2748" opacity=".28"/>
    <g fill="#eef3fb"><circle cx="352" cy="150" r="46"/><circle cx="400" cy="162" r="36"/><circle cx="312" cy="170" r="30"/><rect x="290" y="160" width="140" height="42" rx="21"/></g>
    <g stroke="#bfe6f7" stroke-width="12" stroke-linecap="round"><path d="M318 226 l-14 40"/><path d="M356 226 l-14 40"/><path d="M394 226 l-14 40"/><path d="M336 280 l-10 28"/><path d="M374 280 l-10 28"/></g>
    <path d="M-10 404 C 120 366, 250 386, 522 372 L522 522 L-10 522 Z" fill="url(#snow)"/>
    <g fill="#9db4d6"><ellipse cx="150" cy="452" rx="20" ry="11"/><ellipse cx="206" cy="436" rx="18" ry="10"/><ellipse cx="258" cy="456" rx="20" ry="11"/><ellipse cx="314" cy="438" rx="18" ry="10"/><ellipse cx="366" cy="456" rx="19" ry="10"/></g>
  </g>
  <rect x="7" y="7" width="498" height="498" rx="98" fill="none" stroke="rgba(255,255,255,.55)" stroke-width="6"/>
</svg>`;

const assets = {
  banner: () => page(1800, 600, `
    <img src="${shot('lake_sunset.jpg')}" style="position:absolute;right:-40px;top:-140px;width:1340px;-webkit-mask-image:linear-gradient(90deg,transparent 0%,#000 34%);mask-image:linear-gradient(90deg,transparent 0%,#000 34%)">
    <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(10,18,36,.98) 0%,rgba(10,18,36,.93) 34%,rgba(10,18,36,.32) 60%,rgba(10,18,36,0) 80%)"></div>
    <div style="position:absolute;inset:0;background:linear-gradient(0deg,rgba(10,18,36,.82) 0%,rgba(10,18,36,0) 30%)"></div>
    <div class="isobars" style="-webkit-mask-image:linear-gradient(90deg,#000 0%,#000 36%,transparent 58%);mask-image:linear-gradient(90deg,#000 0%,#000 36%,transparent 58%)"></div>
    <div style="position:absolute;left:84px;top:66px;width:1100px">
      <div class="kicker">${RELEASE.version} · ${RELEASE.name}</div>
      <div class="display title shadow" style="display:inline-block;font-size:124px;line-height:1.02;margin:10px 0 18px;padding-right:14px;white-space:nowrap">Living World</div>
      <div class="horizon" style="width:560px;margin-bottom:24px"></div>
      <div style="font-size:33px;color:${C.paper};line-height:1.3;font-weight:800">Water you can see through, a sky with real clouds,<br>four seasons and weather that arrives. No shader pack.</div>
      <div style="margin-top:28px">${LOADERS.map((l) => `<span class="pill">${l}</span>`).join('')}
        <span style="font-family:Cond,sans-serif;font-size:24px;color:${C.haze};margin-left:6px;letter-spacing:.05em">Minecraft ${versionText} · works with Sodium 0.5</span></div>
    </div>`),

  icon: () => page(512, 512, iconArt, true),

  'card-water': () => card({
    kicker: 'Water', title: 'Water you can<br><i>see through</i>', color: C.water, backdrop: 'lake_dusk.jpg',
    points: ['The bed shows through, bent by the ripples and <b class="hi">fading to blue-green with depth</b>',
      'Trees, builds and clouds are <b class="hi">mirrored on the surface</b>',
      'Glitter under a low sun and <b class="hi">caustics on the sand</b>, from above and when you dive',
      'Smooth in a calm; <b class="hi">waves, surf and damp sand</b> when the wind gets up',
      'No shader pack, and it works with <b class="hi">Sodium 0.5</b>'],
    art: `<div style="position:relative">${framed('lake_noon.jpg', 940, 529, 'center', `border-color:${C.water}`)}${label('Noon, a light breeze', C.water, 'left:18px;top:18px')}
      <div style="position:absolute;right:-24px;bottom:-70px">${framed('lake_sunset.jpg', 420, 236, 'center', `border-color:${C.sun}`)}</div>
      ${label('The same shore at sunset', C.sun, 'right:-6px;bottom:178px;font-size:15px')}</div>
      <div style="margin-top:84px;width:500px">${stats([['Wave trains', '6'], ['Ripple layers', '5'], ['Shader pack', 'None']])}</div>` }),

  'card-sky': () => card({
    kicker: 'Sky and clouds', title: 'A sky worth<br><i>looking up at</i>', color: C.sun, backdrop: 'sky_sunset.jpg',
    points: ['<b class="hi">Three-dimensional clouds</b> that drift with the wind and thicken before rain',
      'Their <b class="hi">shadows cross the land</b> and their reflections cross the water',
      'Gold-to-rose sunsets, thousands of stars and the Milky Way',
      '<b class="hi">Northern lights</b> on cold, clear nights',
      'A <b class="hi">rainbow</b> when a low sun finds wet air'],
    art: grid([['sky_noon.jpg', 'Clouds at noon', C.haze, 'center 30%'], ['sky_sunset.jpg', 'Sunset', C.sun],
      ['sky_night.jpg', 'Stars and the Milky Way', C.storm, '24% 30%', 2], ['sky_aurora.jpg', 'Northern lights', C.aurora]]) }),

  'card-seasons': () => card({
    kicker: 'Seasons', title: 'The year<br><i>turns</i>', color: C.autumn, backdrop: 'season_autumn.jpg',
    points: ['Four seasons counted from the world\'s own days: <b class="hi">8 days each</b> by default',
      'Spring green, an autumn of <b class="hi">gold, orange and red</b>, bare winters',
      'Up to 7 °C warmer in midsummer and <b class="hi">17 °C colder in midwinter</b>',
      'Winter showers <b class="hi">fall as snow</b>, and the ground keeps it',
      'A calendar for the HUD, and <code>/livingworld season</code>'],
    art: `${grid([['season_spring.jpg', 'Spring', C.leaf, 'center 62%'], ['season_summer.jpg', 'Summer', C.sun, 'center 62%'],
      ['season_autumn.jpg', 'Autumn', C.autumn, 'center 62%'], ['season_winter.jpg', 'Winter', C.snow, 'center 62%']], 462, 236)}
      <div style="margin-top:22px;width:560px">${stats([['A season', '8 days'], ['A year', '32 days'], ['Midwinter', '−17 °C']])}</div>` }),

  'card-weather': () => card({
    kicker: 'Weather', title: 'Storms that<br><i>arrive</i>', color: C.storm, backdrop: 'storm.jpg',
    points: ['Clouds close in and the wind picks up <b class="hi">before the first drop</b>',
      'Rain from drizzle to torrential, slanted by the wind and stopped by roofs',
      '<b class="hi">Lightning lights the land and the clouds</b> from where it struck',
      'Wet ground, <b class="hi">puddles that mirror the sky</b>, mud that takes your prints',
      'Sandstorms in the desert, blizzards in the snow'],
    art: `<div style="position:relative">${framed('lightning.jpg', 940, 470, 'center', `border-color:${C.storm}`)}${label('A stroke at dusk', C.storm, 'left:18px;top:18px')}</div>
      <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:14px;margin-top:16px">
        ${[['puddles.jpg', 'After the rain', C.water], ['sandstorm.jpg', 'Sandstorm', C.sun], ['blizzard.jpg', 'Blizzard', C.snow]].map(([f, t, c]) =>
          `<div style="position:relative">${framed(f, 304, 171, 'center', `border-color:${c}`)}${label(t, c, 'left:10px;top:10px;font-size:15px')}</div>`).join('')}
      </div>` }),

  'card-world': () => card({
    kicker: 'Wind, tracks and sound', title: 'A world that<br><i>answers</i>', color: C.leaf, backdrop: 'meadow_wind.jpg',
    points: ['Grass, flowers and leaves <b class="hi">lean and roll with the wind</b>',
      '<b class="hi">Footprints</b> in sand, snow and mud, yours and every creature\'s, worn away by wind and rain',
      'Breath in the cold, shimmer over hot sand, frost at the edge of your view',
      'A soundscape <b class="hi">synthesised as you play</b>: wind, rain on each surface, caves, wildlife',
      'Dropped items tumble and lie where they land'],
    art: `<div style="position:relative">${framed('meadow_wind.jpg', 940, 529, 'center', `border-color:${C.leaf}`)}${label('A meadow in a strong wind', C.leaf, 'left:18px;top:18px')}
      <div style="position:absolute;left:-22px;bottom:-78px">${framed('footprints_sand.jpg', 400, 225, 'center 40%', `border-color:${C.sun}`)}</div>
      <div style="position:absolute;right:-22px;bottom:-78px">${framed('snow_trail.jpg', 400, 225, 'center', `border-color:${C.snow}`)}</div></div>
      <div style="height:96px"></div>` }),

  'card-gameplay': () => card({
    kicker: 'Opt-in, for servers and survival', title: 'Winters you can<br><i>walk on</i>', color: C.snow, backdrop: 'winter_frozen.jpg',
    points: ['Off by default: <code>/livingworld season snow on</code>',
      'Snow settles while it snows and <b class="hi">builds up in layers</b>',
      'Still water <b class="hi">freezes from the banks inward</b>',
      'The thaw removes what winter placed and <b class="hi">nothing you built</b>',
      '<code>/livingworld season crops on</code>: crops grow with the year'],
    art: `<div style="display:grid;grid-template-columns:462px 462px;gap:16px">
        <div style="position:relative">${framed('winter_before.jpg', 462, 300, 'center', `border-color:${C.leaf}`)}${label('Summer', C.leaf, 'left:14px;top:14px')}</div>
        <div style="position:relative">${framed('winter_frozen.jpg', 462, 300, 'center', `border-color:${C.snow}`)}${label('Winter, the same shore', C.snow, 'left:14px;top:14px')}</div>
      </div>
      <div style="margin-top:24px">${stats([['Crops in spring', '×1.25'], ['Crops in winter', '×0.2'], ['Snow builds to', '2 layers', 1.2]])}</div>` }),

  'card-config': () => card({
    kicker: 'Yours to tune', title: 'Every effect<br><i>has a switch</i>', color: C.sun, backdrop: 'lake_noon.jpg',
    points: ['<b class="hi">F9</b> opens the settings: 14 pages, every option explained',
      'Four presets, from <b class="hi">Performance</b> to <b class="hi">Cinematic</b>',
      'On the client alone it works on <b class="hi">any server</b>, vanilla included',
      'Heat and cold are feedback, with survival rules off unless a server wants them',
      'One Accessibility page removes camera motion, flashes and heavy fog'],
    art: `<div style="position:relative">${framed('settings_water.jpg', 940, 529, 'center', `border-color:${C.sun}`)}</div>
      <div style="margin-top:24px;width:640px">${stats([['Presets', '4'], ['Settings pages', '14'], ['Needed on the server', 'Nothing', 1.6]])}</div>` }),
};

const HEADERS = {
  'header-water': ['Water', C.water], 'header-sky': ['Sky and Clouds', C.sun], 'header-seasons': ['Seasons', C.autumn],
  'header-weather': ['Weather', C.storm], 'header-world': ['A World That Answers', C.leaf],
  'header-gameplay': ['Seasonal Gameplay', C.snow], 'header-config': ['Settings and Performance', C.sun],
  'header-compatibility': ['Compatibility', C.water], 'header-faq': ['FAQ', C.haze], 'header-gallery': ['Gallery', C.haze],
};
for (const [name, [title, color]] of Object.entries(HEADERS)) {
  assets[name] = () => header(title, color);
}
assets.divider = divider;

const isTransparent = (name) => name.startsWith('header') || name === 'divider' || name === 'icon';

// ---- two ways to turn a page into a PNG -----------------------------------------------------------------------

async function withPlaywright(names) {
  let chromium;
  try {
    ({ chromium } = require('playwright'));
  } catch (e) {
    return false;
  }
  let browser;
  try {
    browser = await chromium.launch();
  } catch (e) {
    return false;
  }
  const context = await browser.newContext({ deviceScaleFactor: 1 });
  for (const name of names) {
    const { html, width, height } = assets[name]();
    const file = path.join(__dirname, `.render-${name}.html`);
    fs.writeFileSync(file, html);
    const p = await context.newPage();
    await p.setViewportSize({ width, height });
    await p.goto(url(file));
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(250);
    await p.screenshot({ path: path.join(out, `${name}.png`), omitBackground: isTransparent(name) });
    await p.close();
    fs.unlinkSync(file);
    console.log('rendered', name);
  }
  await browser.close();
  return true;
}

function findBrowser() {
  const candidates = [process.env.BROWSER,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', 'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/microsoft-edge'];
  return candidates.find((c) => c && fs.existsSync(c));
}

function withCommandLine(names) {
  const browser = findBrowser();
  if (!browser) {
    throw new Error('No Playwright and no Edge/Chrome found. Install Playwright, or set BROWSER to a Chromium-based browser.');
  }
  const profile = fs.mkdtempSync(path.join(require('os').tmpdir(), 'lw-render-'));
  for (const name of names) {
    const { html, width, height } = assets[name]();
    const file = path.join(__dirname, `.render-${name}.html`);
    fs.writeFileSync(file, html);
    const target = path.join(out, `${name}.png`);
    const result = spawnSync(browser, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
      `--default-background-color=${isTransparent(name) ? '00000000' : 'ff0a1224'}`, `--window-size=${width},${height}`,
      '--virtual-time-budget=5000', `--user-data-dir=${profile}`, `--screenshot=${target}`, url(file)], { stdio: 'pipe' });
    fs.unlinkSync(file);
    if (result.status !== 0 || !fs.existsSync(target)) {
      throw new Error(`${name}: the browser did not write ${target}\n${result.stderr}`);
    }
    console.log('rendered', name);
  }
  fs.rmSync(profile, { recursive: true, force: true });
}

(async () => {
  const wanted = process.argv.slice(2);
  const names = (wanted.length ? wanted : Object.keys(assets)).filter((name) => {
    if (!assets[name]) {
      console.error('unknown asset', name);
    }
    return assets[name];
  });
  fs.mkdirSync(out, { recursive: true });
  if (!(await withPlaywright(names))) {
    withCommandLine(names);
  }
})();
