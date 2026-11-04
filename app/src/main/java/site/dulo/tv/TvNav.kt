package site.dulo.tv

/**
 * Spatial D-pad navigation injected into the page. The site is mouse-first,
 * so we track a "cursor" element and move it to the nearest clickable node in
 * the pressed direction, drawing an outline. OK dispatches a full synthetic
 * mouse sequence so React/Vue handlers fire.
 */
object TvNav {

    val JS: String = """
    (function () {
      if (window.__tvnav) return;
      window.__tvnav = 1;
      var SEL = 'a,button,input,select,textarea,[role="button"],[onclick],[tabindex],.card,.poster,.item,.movie,.episode';
      var cur = null;

      function vis(e) {
        var r = e.getBoundingClientRect(), s = getComputedStyle(e);
        return r.width > 6 && r.height > 6 && r.bottom > 0 && r.top < innerHeight &&
               s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0' &&
               !e.disabled;
      }
      function all() { return Array.prototype.filter.call(document.querySelectorAll(SEL), vis); }
      function mid(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }

      function paint(e) {
        if (cur) cur.style.outline = cur.__oldOutline || '';
        cur = e;
        if (!e) return;
        e.__oldOutline = e.style.outline;
        e.style.outline = '3px solid #E11D2E';
        e.style.outlineOffset = '2px';
      }

      function go(dx, dy) {
        var list = all();
        if (!list.length) return;
        var base;
        if (cur && document.contains(cur) && vis(cur)) base = cur.getBoundingClientRect();
        else base = { left: innerWidth / 2 - 1, top: innerHeight / 2 - 1, width: 2, height: 2 };
        var c0 = mid(base), best = null, bestD = 1e12;
        for (var i = 0; i < list.length; i++) {
          var e = list[i];
          if (e === cur) continue;
          var c = mid(e.getBoundingClientRect());
          var along = dx ? (c.x - c0.x) * dx : (c.y - c0.y) * dy;
          if (along <= 4) continue;
          var perp = dx ? Math.abs(c.y - c0.y) : Math.abs(c.x - c0.x);
          var d = along + perp * 2.2;
          if (d < bestD) { bestD = d; best = e; }
        }
        if (best) {
          paint(best);
          try { best.scrollIntoView({ block: 'center', inline: 'center' }); } catch (err) {}
        }
      }

      function ok() {
        var e = (cur && document.contains(cur)) ? cur : document.activeElement;
        if (!e) return;
        var tag = (e.tagName || '').toUpperCase();
        if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
          e.focus();
          if (window.__tvbridge) window.__tvbridge.setNativeMode(true);
          return;
        }
        var r = e.getBoundingClientRect();
        var x = r.left + r.width / 2, y = r.top + r.height / 2;
        var seq = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];
        for (var i = 0; i < seq.length; i++) {
          var t = seq[i];
          try {
            var C = t.indexOf('pointer') === 0 ? PointerEvent : MouseEvent;
            e.dispatchEvent(new C(t, { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 }));
          } catch (err) {}
        }
        try { if (typeof e.click === 'function') e.click(); } catch (err) {}
      }

      // ---- pop-up kill layer ----------------------------------------------
      window.open = function () { return null; };
      window.alert = function () {};
      window.confirm = function () { return true; };
      window.print = function () {};

      // Remove fixed overlays that hold an ad iframe or an ad link.
      var ADHOST = /(doubleclick|googlesyndication|popads|popcash|propeller|adsterra|onclck|exoclick|juicyads|mgid|taboola|outbrain|revcontent|adnxs|zedo|hilltopads|clickadu)/i;

      function nukeOverlays() {
        var els = document.querySelectorAll('div,section,aside,iframe');
        for (var i = 0; i < els.length; i++) {
          var e = els[i];
          if (e.__checked) continue;
          e.__checked = 1;
          var s = getComputedStyle(e);
          if (s.position !== 'fixed' && s.position !== 'absolute') continue;
          var z = parseInt(s.zIndex, 10);
          if (!(z > 800)) continue;
          var r = e.getBoundingClientRect();
          if (r.width < innerWidth * 0.18 || r.height < innerHeight * 0.10) continue;
          var html = e.innerHTML || '';
          var hit = ADHOST.test(html) || (e.tagName === 'IFRAME' && ADHOST.test(e.src || ''));
          if (!hit) continue;
          // Leave alone anything that clearly is the video player.
          if (e.querySelector && e.querySelector('video')) continue;
          e.style.display = 'none';
        }
      }

      function sweep() {
        try { nukeOverlays(); } catch (err) {}
      }
      sweep();
      setInterval(sweep, 1200);
      new MutationObserver(sweep).observe(document.documentElement, { childList: true, subtree: true });

      window.__tvgo = go;
      window.__tvok = ok;
      window.__tvrecenter = function () { paint(null); };
    })();
    """.trimIndent()
}