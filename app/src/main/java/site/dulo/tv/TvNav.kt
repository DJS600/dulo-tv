package site.dulo.tv

object TvNav {

    val JS: String = """
    (function () {
      if (window.__tvnav2) return;
      window.__tvnav2 = 1;
      var SEL = 'a,button,input,select,textarea,[role="button"],[onclick],[tabindex],.card,.poster,.item,.movie,.episode';
      var cur = null;

      function vis(e) {
        var r = e.getBoundingClientRect(), s = getComputedStyle(e);
        return r.width > 6 && r.height > 6 && r.bottom > 0 && r.top < innerHeight &&
               s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0' && !e.disabled;
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
        var list = all(); if (!list.length) return;
        var base;
        if (cur && document.contains(cur) && vis(cur)) base = cur.getBoundingClientRect();
        else base = { left: innerWidth / 2 - 1, top: innerHeight / 2 - 1, width: 2, height: 2 };
        var c0 = mid(base), best = null, bestD = 1e12;
        for (var i = 0; i < list.length; i++) {
          var e = list[i]; if (e === cur) continue;
          var c = mid(e.getBoundingClientRect());
          var along = dx ? (c.x - c0.x) * dx : (c.y - c0.y) * dy;
          if (along <= 4) continue;
          var perp = dx ? Math.abs(c.y - c0.y) : Math.abs(c.x - c0.x);
          var d = along + perp * 2.2;
          if (d < bestD) { bestD = d; best = e; }
        }
        if (best) { paint(best); try { best.scrollIntoView({ block: 'center', inline: 'center' }); } catch (err) {} }
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
        var r = e.getBoundingClientRect(), x = r.left + r.width / 2, y = r.top + r.height / 2;
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

      // ---------- pop-up suppression ----------
      var stub = { focus: function () {}, close: function () {}, postMessage: function () {},
                   location: {}, document: {}, closed: true };
      window.open = function () { return stub; };
      window.alert = function () {};
      window.confirm = function () { return true; };
      window.print = function () {};

      var ADHOST = /(doubleclick|googlesyndication|popads|popcash|propeller|adsterra|onclck|exoclick|juicyads|mgid|taboola|outbrain|revcontent|adnxs|zedo|hilltopads|clickadu|adcash|tsyndicate|media-adv|admo|popmyads|bidsxchange|click|trk|traf|adserv)/i;
      var PLAYER = /(player|embed|vidsrc|2embed|multiembed|videasy|vidlink|vidfast|111movies|smashystream|streamtape|filemoon|dood|voe|mixdrop|upstream|mp4upload|vidcloud|rabbitstream|megacloud|hydrax|streamwish|abyss|superembed|vidbinge|tmdb)/i;
      var CLOSE = /^(×|✕|✖|╳|x|X|close|CLOSE|Close|skip|SKIP|Skip|continue|CONTINUE|Continue|no thanks|No thanks|No Thanks|✗|→|»)$/;

      function vpArea() { return innerWidth * innerHeight; }

      function playerish(e) {
        if (!e.querySelector) return false;
        if (e.querySelector('video')) return true;
        var f = e.querySelector('iframe');
        if (f && PLAYER.test(f.src || '')) return true;
        return false;
      }

      function clickClose(e) {
        var n = e.querySelectorAll('button,a,span,div,i,svg,img');
        var lim = Math.min(n.length, 90);
        for (var i = 0; i < lim; i++) {
          var t = (n[i].textContent || '').trim();
          if (t.length && t.length <= 12 && CLOSE.test(t)) {
            try { n[i].click(); } catch (err) {}
            return true;
          }
          var al = ((n[i].getAttribute && n[i].getAttribute('aria-label')) || '').toLowerCase();
          if (al.indexOf('close') >= 0) { try { n[i].click(); } catch (err) {} return true; }
        }
        return false;
      }

      function hide(e, why) {
        if (!e || e.__hidden) return;
        e.__hidden = 1;
        e.style.setProperty('display', 'none', 'important');
        try { console.log('[tvblock] ' + why + ' <' + e.tagName + ' ' + (e.className || '') + '>'); } catch (err) {}
      }

      function sweep() {
        try {
          var els = document.querySelectorAll('div,section,aside,iframe,ins');
          var vpa = vpArea();
          for (var i = 0; i < els.length; i++) {
            var e = els[i];
            if (e.__hidden || e.__checked) continue;
            e.__checked = 1;
            var s = getComputedStyle(e);
            var fixed = s.position === 'fixed';
            if (!fixed && s.position !== 'absolute') continue;
            var z = parseInt(s.zIndex, 10) || 0;
            if (fixed ? z < 50 : z < 100) continue;
            var r = e.getBoundingClientRect();
            if (r.width < 40 || r.height < 40) continue;
            var frac = (r.width * r.height) / vpa;
            if (frac < 0.04) continue;
            if (playerish(e)) continue;
            if (e.querySelector('input,textarea,select')) continue;
            var html = (e.innerHTML || '').toLowerCase();
            var adish = ADHOST.test(html) || e.tagName === 'IFRAME' || e.tagName === 'INS';
            if (!adish && frac < 0.45) continue;
            clickClose(e);
            hide(e, (adish ? 'ad' : 'cover') + ' f=' + frac.toFixed(2) + ' z=' + z);
          }
          if (document.body && getComputedStyle(document.body).overflow === 'hidden')
            document.body.style.setProperty('overflow', 'auto', 'important');
          if (document.documentElement.style.overflow === 'hidden')
            document.documentElement.style.removeProperty('overflow');
        } catch (err) {}
      }

      sweep();
      setInterval(sweep, 700);
      new MutationObserver(function () { setTimeout(sweep, 50); })
        .observe(document.documentElement, { childList: true, subtree: true });

      window.__tvnuke = function () {
        var a = document.querySelectorAll('*');
        for (var i = 0; i < a.length; i++) a[i].__checked = 0;
        sweep();
      };

      window.__tvgo = go;
      window.__tvok = ok;
      window.__tvrecenter = function () { paint(null); };
    })();
    """.trimIndent()
}