// Vista alternativa su Canvas 2D, usata quando WebGL non è disponibile.
// Mostra gli stessi dati con spostamento e ingrandimento; manca solo la mappa di base.
import { makeProjection, drawSceneCanvas } from './scene.js';
import { mercX, mercY, invMercX, invMercY } from './util.js';

export class CanvasView {
  constructor(container) {
    this.kind = 'canvas';
    this.container = container;
    this.canvas = document.createElement('canvas');
    this.canvas.className = 'vista-canvas';
    this.canvas.tabIndex = 0;
    this.canvas.setAttribute('role', 'application');
    this.canvas.setAttribute('aria-label', 'Mappa in modalità semplificata. Frecce per spostarsi, più e meno per ingrandire.');
    container.appendChild(this.canvas);
    this.ctx = this.canvas.getContext('2d');
    this.cx = mercX(12.62); this.cy = mercY(46.01);
    this.scale = 256 * 2 ** 12;
    this.groups = [];
    this.bg = '#ffffff';
    this.handlers = { click: [], moveend: [], move: [] };
    this.ready = true;
    this._bind();
    new ResizeObserver(() => this.resize()).observe(container);
    this.resize();
  }

  on(ev, fn) { (this.handlers[ev] ||= []).push(fn); }
  emit(ev, e) { for (const fn of this.handlers[ev] || []) fn(e); }

  get zoom() { return Math.log2(this.scale / 256); }

  resize() {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const { clientWidth: w, clientHeight: h } = this.container;
    this.W = w; this.H = h;
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.canvas.style.width = w + 'px';
    this.canvas.style.height = h + 'px';
    this.dpr = dpr;
    this.requestRender();
  }

  setScene(groups, bg) {
    this.groups = groups;
    this.bg = bg;
    this.requestRender();
  }

  requestRender() {
    if (this._raf) return;
    this._raf = requestAnimationFrame(() => {
      this._raf = null;
      this.render();
    });
  }

  render() {
    const ctx = this.ctx;
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    const proj = makeProjection(this.cx, this.cy, this.scale, this.W, this.H);
    const k = Math.max(0.35, Math.min(3, 2 ** (this.zoom - 14)));
    drawSceneCanvas(ctx, this.groups, proj, { k, bg: this.bg, tol: 0.6 });
  }

  toLngLat(x, y) {
    const mx = this.cx + (x - this.W / 2) / this.scale;
    const my = this.cy + (y - this.H / 2) / this.scale;
    return { lng: invMercX(mx), lat: invMercY(my) };
  }

  bounds() {
    const a = this.toLngLat(0, this.H), b = this.toLngLat(this.W, 0);
    return [a.lng, a.lat, b.lng, b.lat];
  }

  fit(bbox, { padding = 40 } = {}) {
    if (!bbox) return;
    const x0 = mercX(bbox[0]), x1 = mercX(bbox[2]), y0 = mercY(bbox[3]), y1 = mercY(bbox[1]);
    this.cx = (x0 + x1) / 2; this.cy = (y0 + y1) / 2;
    this.scale = Math.min((this.W - 2 * padding) / Math.max(1e-9, x1 - x0), (this.H - 2 * padding) / Math.max(1e-9, y1 - y0));
    this.scale = Math.min(this.scale, 256 * 2 ** 18);
    this.requestRender();
    this.emit('moveend');
  }

  zoomBy(f, x = this.W / 2, y = this.H / 2) {
    const before = this.toLngLat(x, y);
    this.scale = Math.max(256 * 2 ** 3, Math.min(256 * 2 ** 19, this.scale * f));
    const bx = mercX(before.lng), by = mercY(before.lat);
    this.cx = bx - (x - this.W / 2) / this.scale;
    this.cy = by - (y - this.H / 2) / this.scale;
    this.requestRender();
    this.emit('move');
  }

  panBy(dx, dy) {
    this.cx -= dx / this.scale;
    this.cy -= dy / this.scale;
    this.requestRender();
    this.emit('move');
  }

  _bind() {
    const c = this.canvas;
    const pointers = new Map();
    let moved = 0, lastPinch = null, endTimer;
    const settle = () => { clearTimeout(endTimer); endTimer = setTimeout(() => this.emit('moveend'), 200); };
    c.addEventListener('pointerdown', (e) => {
      c.setPointerCapture(e.pointerId);
      pointers.set(e.pointerId, [e.offsetX, e.offsetY]);
      moved = 0;
    });
    c.addEventListener('pointermove', (e) => {
      const p = pointers.get(e.pointerId);
      if (!p) return;
      if (pointers.size === 1) {
        const dx = e.offsetX - p[0], dy = e.offsetY - p[1];
        moved += Math.abs(dx) + Math.abs(dy);
        this.panBy(dx, dy);
      }
      pointers.set(e.pointerId, [e.offsetX, e.offsetY]);
      if (pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        const d = Math.hypot(a[0] - b[0], a[1] - b[1]);
        if (lastPinch) this.zoomBy(d / lastPinch, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
        lastPinch = d;
        moved += 10;
      }
    });
    const up = (e) => {
      if (pointers.size === 1 && moved < 5) {
        this.emit('click', { lngLat: this.toLngLat(e.offsetX, e.offsetY), point: { x: e.offsetX, y: e.offsetY } });
      }
      pointers.delete(e.pointerId);
      if (pointers.size < 2) lastPinch = null;
      settle();
    };
    c.addEventListener('pointerup', up);
    c.addEventListener('pointercancel', up);
    c.addEventListener('wheel', (e) => {
      e.preventDefault();
      this.zoomBy(e.deltaY < 0 ? 1.2 : 1 / 1.2, e.offsetX, e.offsetY);
      settle();
    }, { passive: false });
    c.addEventListener('keydown', (e) => {
      const step = 80;
      const map = { ArrowLeft: [step, 0], ArrowRight: [-step, 0], ArrowUp: [0, step], ArrowDown: [0, -step] };
      if (map[e.key]) { e.preventDefault(); this.panBy(...map[e.key]); settle(); }
      else if (e.key === '+' || e.key === '=') { this.zoomBy(1.5); settle(); }
      else if (e.key === '-') { this.zoomBy(1 / 1.5); settle(); }
    });
  }
}
