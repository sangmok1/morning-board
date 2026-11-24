// 문자 그리드 픽셀 아이콘 (Android Pixel.kt 이식). '.' = 투명
const PIXEL_PALETTE = {
  K: '#1E1E2E', W: '#F8F9FA', G: '#BAC0CC', D: '#868E9C',
  Y: '#FFD43B', O: '#F59F00', B: '#339AF0', N: '#2B3A67',
  L: '#4A5C9E', R: '#E8590C', r: '#FF922B', H: '#2F9E44',
  h: '#51CF66', P: '#7048E8', p: '#9775FA', T: '#22B8CF',
  t: '#66D9E8', J: '#1864AB', j: '#4DABF7', U: '#FA5252',
  u: '#FF8787', M: '#8D6E63',
};

const SPRITES = {
  SUN: [
    "................",".......YY.......","..Y....YY....Y..","...Y........Y...",
    "......OOOO......",".....OYYYYO.....","....OYYYYYYO....","YY..OYYYYYYO..YY",
    "YY..OYYYYYYO..YY","....OYYYYYYO....",".....OYYYYO.....","......OOOO......",
    "...Y........Y...","..Y....YY....Y..",".......YY.......","................",
  ],
  MOON: [
    "................",".......OOOO.....",".....OOYYO......","....OYYYO.......",
    "...OYYYO........","...OYYO.........","..OYYYO.........","..OYYYO.........",
    "..OYYYO.........","..OYYYYO........","...OYYYYO.....O.","...OYYYYYOOOOO..",
    "....OYYYYYYYYO..",".....OOYYYYOO...",".......OOOO.....","................",
  ],
  CLOUD: [
    "................","................","................","......KKKK......",
    ".....KWWWWK.....","...KKWWWWWWK....","..KWWWWWWWWWKK..",".KWWWWWWWWWWWWK.",
    "KWWWWWWWWWWWWWWK","KWWWWWWWWWWWWWWK","KGWWWWWWWWWWWWGK",".KGGGGGGGGGGGGK.",
    "..KKKKKKKKKKKK..","................","................","................",
  ],
  PARTLY: [
    "..Y.....Y.......","...Y...Y........",".....OOOO.......","YY..OYYYYO..YY..",
    "...OYYYYYYO.....","...OYYYKKKK.....","...OYYKWWWWK....","....OKWWWWWWKK..",
    "..YKKWWWWWWWWWK.",".YKWWWWWWWWWWWWK","..KWWWWWWWWWWWWK","..KGWWWWWWWWWWGK",
    "...KGGGGGGGGGGK.","....KKKKKKKKKK..","................","................",
  ],
  RAIN: [
    "................","......KKKK......",".....KGGGGK.....","...KKGGGGGGK....",
    "..KGGGGGGGGGKK..",".KGGGGGGGGGGGGK.","KGGGGGGGGGGGGGGK","KDGGGGGGGGGGGGDK",
    ".KDDDDDDDDDDDDK.","..KKKKKKKKKKKK..","................","...B....B....B..",
    "..B....B....B...","................",".B....B....B....","B....B....B.....",
  ],
  SNOW: [
    "................","......KKKK......",".....KWWWWK.....","...KKWWWWWWK....",
    "..KWWWWWWWWWKK..",".KWWWWWWWWWWWWK.","KWWWWWWWWWWWWWWK","KGWWWWWWWWWWWWGK",
    ".KGGGGGGGGGGGGK.","..KKKKKKKKKKKK..","................","..B....B....B...",
    ".BWB..BWB..BWB..","..B....B....B...","....B....B......","...BWB..BWB.....",
  ],
  THUNDER: [
    "................","......KKKK......",".....KDDDDK.....","...KKDDDDDDK....",
    "..KDDDDDDDDDKK..",".KDDDDDDDDDDDDK.","KDDDDDDDDDDDDDDK","KDDDDDDDDDDDDDDK",
    ".KDDDDDOYDDDDDK.","..KKKKOYKKKKKK..","......OYY.......",".....OYYYY......",
    ".......OY.......","......OY........","......Y.........","................",
  ],
  FOG: [
    "................","................","................","..GGGGGGGGGG....",
    "................","....GGGGGGGGGGG.","................",".GGGGGGGGGGGG...",
    "................","...GGGGGGGGGGGG.","................","..GGGGGGGGGG....",
    "................","................","................","................",
  ],
  LONG_PADDING: [
    "....KKKKKKKK....","...KNNNNNNNNK...","..KNNKKKKKKNNK..","..KNK......KNK..",
    ".KNNNKKKKKKNNNK.","KNLLLLLNNLLLLLNK","KNNNNNNNNNNNNNNK","KNLKLLLNNLLLKLNK",
    "KNNKNNNNNNNNKNNK","KNLKLLLNNLLLKLNK","KKKKNNNNNNNNKKKK","...KLLLNNLLLK...",
    "...KNNNNNNNNK...","...KLLLNNLLLK...","...KNNNNNNNNK...","...KKKKKKKKKK...",
  ],
  SHORT_PADDING: [
    "................","....KKKKKKKK....","...KRRRRRRRRK...","..KRRKKKKKKRRK..",
    ".KRRRK....KRRRK.","KRrrrrrRRrrrrrRK","KRRRRRRRRRRRRRRK","KRrKrrrRRrrrKrRK",
    "KRRKRRRRRRRRKRRK","KRrKrrrRRrrrKrRK","KRRKRRRRRRRRKRRK","KKKKRRRRRRRRKKKK",
    "...KKKKKKKKKK...","................","................","................",
  ],
  HOODIE: [
    "................",".....KKKKKK.....","....KHHHHHHK....","...KHHKKKKHHK...",
    "..KHHK....KHHK..",".KHHHHKKKKHHHHK.","KHHHHHHWHHWHHHHK","KHHKHHHWHHWHKHHK",
    "KHHKHHHHHHHHKHHK","KHHKHhhhhhhHKHHK","KHHKHhHHHHhHKHHK","KKKKHhhhhhhHKKKK",
    "...KHHHHHHHHK...","...KKKKKKKKKK...","................","................",
  ],
  SWEATSHIRT: [
    "................","................","....KKK..KKK....","..KKPPPKKPPPKK..",
    ".KPPPPPPPPPPPPK.","KPPPPPPPPPPPPPPK","KPPKPPPPPPPPKPPK","KPPKPPPPPPPPKPPK",
    "KPPKPPPPPPPPKPPK","KPPKPPPPPPPPKPPK","KppKPPPPPPPPKppK","KKKKpppppppppKKK",
    "...KKKKKKKKKK...","................","................","................",
  ],
  TSHIRT: [
    "................","................","................","....KKK..KKK....",
    "..KKTTTKKTTTKK..",".KTTTTTTTTTTTTK.","KTTTTTTTTTTTTTTK","KttKTTTTTTTTKttK",
    ".KKKTTTTTTTTKKK.","...KTTTTTTTTK...","...KTTTTTTTTK...","...KTTTTTTTTK...",
    "...KKKKKKKKKK...","................","................","................",
  ],
  SHORTS: [
    "................","................","................","................",
    "...KKKKKKKKKK...","...KJJJJJJJJK...","...KjjjjjjjjK...","..KJJJJJJJJJJK..",
    "..KJJJJKKJJJJK..","..KJJJJKKJJJJK..",".KJJJJK..KJJJJK.",".KKKKKK..KKKKKK.",
    "................","................","................","................",
  ],
  PANTS: [
    "................","....KKKKKKKK....","....KJJJJJJK....","....KjjjjjjK....",
    "...KJJJJJJJJK...","...KJJJKKJJJK...","...KJJJKKJJJK...","...KJJJKKJJJK...",
    "..KJJJK..KJJJK..","..KJJJK..KJJJK..","..KJJJK..KJJJK..","..KJJJK..KJJJK..",
    "..KJJJK..KJJJK..","..KjjjK..KjjjK..","..KKKKK..KKKKK..","................",
  ],
  UMBRELLA: [
    ".......KK.......",".....KKUUKK.....","...KKUUuUUUKK...","..KUUUuUUUuUUK..",
    ".KUUUuUUUUUuUUK.",".KUUuUUUUUUUuUK.","KUUUuUUUUUUUuUUK","KKKKKKKMMKKKKKKK",
    ".......MM.......",".......MM.......",".......MM.......",".......MM.......",
    ".......MM.......","...MM..MM.......","...MMMMMM.......","....MMMM........",
  ],
};

// 날씨 코드 → 아이콘
function iconFor(code, isDay = true) {
  if (code === 0 || code === 1) return isDay ? SPRITES.SUN : SPRITES.MOON;
  if (code === 2) return isDay ? SPRITES.PARTLY : SPRITES.CLOUD;
  if (code === 3) return SPRITES.CLOUD;
  if (code === 45 || code === 48) return SPRITES.FOG;
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return SPRITES.SNOW;
  if (code >= 95 && code <= 99) return SPRITES.THUNDER;
  if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return SPRITES.RAIN;
  return SPRITES.CLOUD;
}

/**
 * 스프라이트를 canvas 로 렌더해 반환.
 * tile=true 면 밝은 둥근 타일 위에 아이콘(옷차림/현재날씨), false 면 아이콘만(주간).
 */
function makeSpriteCanvas(sprite, sizePx, { tile = true, tileBg = '#E9EDF3' } = {}) {
  const dpr = window.devicePixelRatio || 1;
  const c = document.createElement('canvas');
  c.width = Math.round(sizePx * dpr);
  c.height = Math.round(sizePx * dpr);
  c.style.width = sizePx + 'px';
  c.style.height = sizePx + 'px';
  const ctx = c.getContext('2d');
  ctx.scale(dpr, dpr);
  ctx.imageSmoothingEnabled = false;

  const px = sizePx;
  if (tile) {
    const r = px * 0.18;
    roundRect(ctx, 0, 0, px, px, r);
    ctx.fillStyle = tileBg;
    ctx.fill();
  }
  const cols = Math.max(...sprite.map(s => s.length));
  const rows = sprite.length;
  const grid = Math.max(cols, rows) || 1;
  const fill = tile ? 0.72 : 1.0;
  const cell = (px * fill) / grid;
  const offX = (px - cell * cols) / 2;
  const offY = (px - cell * rows) / 2;
  for (let y = 0; y < rows; y++) {
    const line = sprite[y];
    for (let x = 0; x < line.length; x++) {
      const color = PIXEL_PALETTE[line[x]];
      if (!color) continue;
      ctx.fillStyle = color;
      ctx.fillRect(offX + x * cell, offY + y * cell, cell + 0.6, cell + 0.6);
    }
  }
  return c;
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
