(function () {
  'use strict';
  var names = Object.create(null);

  function remember(node) {
    var text = (node.textContent || '').trim();
    var color = node.getAttribute('data-rating-color');
    if (text && color) names[text] = { color: color, bold: /font-weight:\s*700/.test(node.getAttribute('style') || '') };
  }

  function decorate(root) {
    (root || document).querySelectorAll('[data-rating-color]').forEach(function (node) {
      var color = node.getAttribute('data-rating-color');
      if (color && !node.style.color) node.style.color = color;
      remember(node);
    });
    (root || document).querySelectorAll('.notification h2').forEach(function (node) {
      var entry = names[(node.textContent || '').trim()];
      if (entry) {
        node.style.color = entry.color;
        if (entry.bold) node.style.fontWeight = '700';
      }
    });
  }

  function setDialogOpen(dialog, open) {
    if (!dialog) return;
    dialog.classList.toggle('is-open', open);
    dialog.setAttribute('aria-hidden', open ? 'false' : 'true');
    document.documentElement.classList.toggle('rating-dialog-open', open);
    if (open) {
      var focusTarget = dialog.querySelector('input:not([disabled]), button:not([disabled])');
      if (focusTarget) window.setTimeout(function () { focusTarget.focus(); }, 0);
    }
  }

  function closeDialogFrom(node) {
    setDialogOpen(node.closest('.rating-page-dialog'), false);
  }

  function bindDialogs(root) {
    (root || document).querySelectorAll('[data-rating-open]').forEach(function (button) {
      if (button.getAttribute('data-rating-dialog-bound') === '1') return;
      button.setAttribute('data-rating-dialog-bound', '1');
      button.addEventListener('click', function () {
        setDialogOpen(document.getElementById(button.getAttribute('data-rating-open')), true);
      });
    });
    (root || document).querySelectorAll('[data-rating-close]').forEach(function (button) {
      if (button.getAttribute('data-rating-dialog-bound') === '1') return;
      button.setAttribute('data-rating-dialog-bound', '1');
      button.addEventListener('click', function () { closeDialogFrom(button); });
    });
    (root || document).querySelectorAll('[data-rating-registration-form]').forEach(function (form) {
      if (form.getAttribute('data-rating-form-bound') === '1') return;
      form.setAttribute('data-rating-form-bound', '1');
      form.addEventListener('submit', function () {
        var submit = form.querySelector('button[type="submit"]');
        if (submit) {
          submit.disabled = true;
          submit.setAttribute('aria-busy', 'true');
        }
      });
    });
  }

  function bindSettledEditForms(root) {
    (root || document).querySelectorAll('.rating-settled-edit-form').forEach(function (form) {
      if (form.getAttribute('data-rating-settled-bound') === '1') return;
      form.setAttribute('data-rating-settled-bound', '1');
      var lock = function () {
        form.querySelectorAll('input, select, textarea').forEach(function (control) {
          if (control.getAttribute('name') !== 'content' && !control.disabled) control.disabled = true;
        });
      };
      lock();
      new MutationObserver(lock).observe(form, {
        attributes: true,
        subtree: true,
        attributeFilter: ['disabled'],
      });
      window.setTimeout(lock, 0);
    });
  }

  function pad(value) {
    return String(value).padStart(2, '0');
  }

  function renderCountdown(node) {
    var startAt = Number(node.getAttribute('data-start-at'));
    var endAt = Number(node.getAttribute('data-end-at'));
    var label = node.querySelector('[data-rating-countdown-label]');
    var clock = node.querySelector('[data-rating-countdown-clock]');
    var now = Date.now();
    var phase = now < startAt ? 'before' : (now < endAt ? 'ongoing' : 'ended');
    var previousPhase = node.getAttribute('data-rating-countdown-phase');
    var visibility = node.getAttribute('data-countdown-visibility') || 'always';
    node.setAttribute('data-rating-countdown-phase', phase);
    if (previousPhase === 'before' && phase !== 'before') {
      var redirectUrl = node.getAttribute('data-start-redirect');
      if (redirectUrl) {
        window.location.assign(redirectUrl);
        return false;
      }
    }
    var visible = visibility === 'always'
      || (visibility === 'before' && phase === 'before')
      || (visibility === 'ongoing' && phase !== 'before');
    node.hidden = !visible;
    if (!visible) return visibility === 'ongoing' && phase === 'before';
    var target;
    if (phase === 'before') {
      label.textContent = '距离比赛开始还有：';
      target = startAt;
    } else if (phase === 'ongoing') {
      label.textContent = '距离比赛结束还有：';
      target = endAt;
      document.querySelectorAll('[data-rating-before-start-actions]').forEach(function (actions) {
        actions.hidden = true;
      });
    } else {
      label.textContent = '比赛已结束';
      clock.textContent = '';
      clock.hidden = true;
      document.querySelectorAll('[data-rating-before-start-actions]').forEach(function (actions) {
        actions.hidden = true;
      });
      return false;
    }
    var seconds = Math.max(0, Math.floor((target - now) / 1000));
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var remainingSeconds = seconds % 60;
    clock.hidden = false;
    var hoursNode = node.querySelector('[data-rating-countdown-hours]');
    if (hoursNode) {
      hoursNode.textContent = pad(hours);
      node.querySelector('[data-rating-countdown-minutes]').textContent = pad(minutes);
      node.querySelector('[data-rating-countdown-seconds]').textContent = pad(remainingSeconds);
    } else {
      clock.textContent = pad(hours) + ':' + pad(minutes) + ':' + pad(remainingSeconds);
    }
    return true;
  }

  function bindCountdowns(root) {
    (root || document).querySelectorAll('[data-rating-countdown]').forEach(function (node) {
      if (node.getAttribute('data-rating-countdown-bound') === '1') return;
      node.setAttribute('data-rating-countdown-bound', '1');
      renderCountdown(node);
      var timer = window.setInterval(function () {
        if (!document.documentElement.contains(node) || !renderCountdown(node)) window.clearInterval(timer);
      }, 1000);
    });
  }

  function bindBoardTables(root) {
    var viewports = Array.from((root || document).querySelectorAll('[data-rating-board-fit]'));
    if (root && root.closest) {
      var parent = root.closest('[data-rating-board-fit]');
      if (parent) viewports.push(parent);
    }
    viewports.forEach(function (viewport) {
      if (viewport.ratingBoardUpdate) {
        viewport.ratingBoardUpdate();
        return;
      }
      var slot = viewport.querySelector('.rating-board-fit-slot');
      var inner = viewport.querySelector('.rating-board-fit-inner');
      var table = inner && inner.querySelector('table');
      if (!slot || !inner || !table) return;
      var frame = 0;
      var observer;
      function update() {
        window.cancelAnimationFrame(frame);
        frame = window.requestAnimationFrame(function () {
          if (!document.documentElement.contains(viewport)) {
            if (observer) observer.disconnect();
            return;
          }
          var baseWidth = Number(inner.getAttribute('data-board-width')) || 900;
          var empty = table.querySelector('[data-rating-board-empty]');
          if (empty) empty.hidden = !!table.querySelector('[data-rid]');
          inner.style.width = baseWidth + 'px';
          var naturalWidth = Math.max(baseWidth, table.scrollWidth, table.offsetWidth);
          inner.style.width = naturalWidth + 'px';
          var scale = Math.min(1, Math.max(1, viewport.clientWidth) / naturalWidth);
          inner.style.transform = 'scale(' + scale + ')';
          slot.style.width = (naturalWidth * scale) + 'px';
          viewport.style.height = Math.ceil(inner.offsetHeight * scale) + 'px';
        });
      }
      viewport.ratingBoardUpdate = update;
      if (window.ResizeObserver) {
        observer = new ResizeObserver(update);
        observer.observe(viewport);
        observer.observe(table);
      } else window.addEventListener('resize', update);
      if (document.fonts) document.fonts.ready.then(update);
      update();
    });
  }

  function bindBoardFilters(root) {
    (root || document).querySelectorAll('.rating-scoreboard-card.rating-external-board').forEach(function (card) {
      if (card.getAttribute('data-board-filters-bound')) return;
      card.setAttribute('data-board-filters-bound', '1');
      var search = card.querySelector('[data-rating-board-search]');
      var participation = card.querySelector('[data-rating-board-participation]');
      var frame = 0;
      function filter() {
        window.cancelAnimationFrame(frame);
        frame = window.requestAnimationFrame(function () {
          var keyword = (search ? search.value : '').trim().toLocaleLowerCase();
          var kind = participation ? participation.value : 'all';
          card.querySelectorAll('[data-rating-board-participant]').forEach(function (row) {
            var unrank = row.getAttribute('data-unrank') === '1';
            var visible = (kind === 'all' || (kind === 'rank' && !unrank) || (kind === 'unrank' && unrank))
              && (row.getAttribute('data-board-search') || '').toLocaleLowerCase().includes(keyword);
            var display = visible ? '' : 'none';
            if (row.style.display !== display) row.style.display = display;
          });
          card.querySelectorAll('[data-rating-board-fit]').forEach(function (viewport) {
            if (viewport.ratingBoardUpdate) viewport.ratingBoardUpdate();
          });
        });
      }
      if (search) search.addEventListener('input', filter);
      if (participation) participation.addEventListener('change', filter);
      // Reapply search after Hydro's asynchronous PJAX/filter updates.
      new MutationObserver(function (records) {
        if (records.some(function (record) {
          return (record.type === 'childList' && record.target.closest('table'))
            || (record.type === 'attributes' && record.target.matches('[data-rating-board-participant]'));
        })) filter();
      }).observe(card, {
        childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'],
      });
      filter();
    });
  }

  function bindRecordFilters(root) {
    (root || document).querySelectorAll('[data-rating-record-filter]').forEach(function (form) {
      if (form.getAttribute('data-record-filter-bound')) return;
      form.setAttribute('data-record-filter-bound', '1');
      var card = form.closest('.rating-submissions-card');
      var results = card && card.querySelector('[data-rating-record-results]');
      if (!results) return;
      var feedback = form.querySelector('[data-rating-record-status]');
      var timer;
      var request;
      var revision = 0;
      var composing = false;
      var socket;
      var retry;
      var heartbeat;
      var page = Number(form.getAttribute('data-record-page')) || 1;
      var limit = Number(form.getAttribute('data-record-limit')) || 20;
      var stopped = false;

      function message(value) {
        if (!feedback) return;
        feedback.textContent = value || '';
        feedback.hidden = !value;
      }

      function closeSocket() {
        window.clearTimeout(retry);
        window.clearInterval(heartbeat);
        if (socket) {
          socket.onclose = null;
          socket.close();
          socket = null;
        }
      }

      function emptyRow() {
        var tbody = results.querySelector('tbody');
        if (!tbody || tbody.querySelector('[data-rid]')) return;
        if (!tbody.querySelector('[data-rating-board-empty]')) {
          var row = tbody.insertRow();
          row.setAttribute('data-rating-board-empty', '');
          var cell = row.insertCell();
          cell.colSpan = results.querySelectorAll('thead th').length;
          cell.textContent = 'No submissions';
        }
      }

      function connectSocket(socketUrl) {
        closeSocket();
        if (!socketUrl || !window.WebSocket || stopped || !form.isConnected) return;
        var context = window.UiContext || {};
        if (typeof context === 'string') {
          try { context = JSON.parse(context); } catch (_) { context = {}; }
        }
        var url = new URL((context.ws_prefix || '/') + socketUrl, window.location.href);
        url.protocol = url.protocol.replace('http', 'ws');
        if (url.host !== window.location.host && document.cookie.includes('sid=')) {
          url.searchParams.set('sid', document.cookie.split('sid=')[1].split(';')[0]);
        }
        var current = new WebSocket(url.href);
        socket = current;
        current.onopen = function () {
          if (socket !== current) return;
          current.send(JSON.stringify({ rids: Array.from(results.querySelectorAll('[data-rid]')).map(function (row) {
            return row.getAttribute('data-rid');
          }) }));
          heartbeat = window.setInterval(function () {
            if (current.readyState === 1) current.send('ping');
          }, 30000);
        };
        current.onmessage = function (event) {
          if (socket !== current || !form.isConnected) return;
          if (event.data === 'ping') { current.send('pong'); return; }
          if (event.data === 'pong') return;
          var data;
          try { data = JSON.parse(event.data); } catch (_) { return; }
          if (data.error) { closeSocket(); return; }
          var tbody = results.querySelector('tbody');
          if (!tbody) return;
          if (data.remove) {
            var removed = false;
            Array.from(tbody.querySelectorAll('[data-rid]')).forEach(function (row) {
              if (row.getAttribute('data-rid') === data.remove) { row.remove(); removed = true; }
            });
            if (removed && results.querySelector('.next[data-rating-record-page]')) { load(page); return; }
            emptyRow();
          } else if (typeof data.html === 'string') {
            var holder = document.createElement('tbody');
            holder.innerHTML = data.html;
            var row = holder.querySelector('tr[data-rid]');
            if (!row) return;
            var existing = Array.from(tbody.querySelectorAll('[data-rid]')).find(function (item) {
              return item.getAttribute('data-rid') === row.getAttribute('data-rid');
            });
            if (existing) existing.replaceWith(row);
            else {
              if (page > 1 || new URLSearchParams(window.location.search).has('nopush')) return;
              tbody.prepend(row);
              var rows = tbody.querySelectorAll('[data-rid]');
              if (rows.length > limit) {
                rows[rows.length - 1].remove();
                if (!results.querySelector('.next[data-rating-record-page]')) { load(1); return; }
              }
            }
          }
          bindBoardTables(results);
        };
        current.onclose = function (event) {
          if (socket !== current) return;
          window.clearInterval(heartbeat);
          if (event.code >= 4000 || stopped || !form.isConnected) return;
          retry = window.setTimeout(function () { connectSocket(socketUrl); }, 5000);
        };
      }

      async function load(nextPage) {
        window.clearTimeout(timer);
        closeSocket();
        if (request) request.abort();
        var currentRevision = ++revision;
        var controller = new AbortController();
        request = controller;
        var url = new URL(form.getAttribute('data-record-url'), window.location.href);
        url.search = new URLSearchParams(new FormData(form)).toString();
        if (nextPage > 1) url.searchParams.set('page', nextPage);
        var publicUrl = url.pathname + url.search;
        url.searchParams.set('ratingDynamic', '1');
        results.setAttribute('aria-busy', 'true');
        message('');
        try {
          var response = await fetch(url.href, {
            credentials: 'same-origin', signal: controller.signal, headers: { Accept: 'application/json' },
          });
          if (!response.ok) throw new Error('Search failed');
          var data = await response.json();
          if (currentRevision !== revision || !form.isConnected) return;
          if (typeof data.html !== 'string') throw new Error('Invalid search response');
          results.innerHTML = data.html;
          page = Number(data.page) || nextPage;
          limit = Number(data.limit) || limit;
          form.setAttribute('data-record-page', page);
          form.setAttribute('data-record-socket', data.socketUrl || '');
          window.history.replaceState(window.history.state, '', publicUrl);
          bindBoardTables(results);
          connectSocket(data.socketUrl);
        } catch (error) {
          if (error.name !== 'AbortError' && currentRevision === revision) message('搜索失败，请点击 Refresh 重试。');
        } finally {
          if (currentRevision === revision) results.removeAttribute('aria-busy');
        }
      }

      function schedule() {
        window.clearTimeout(timer);
        // Invalidate pending responses immediately, before the next debounced request starts.
        revision++;
        if (request) request.abort();
        closeSocket();
        timer = window.setTimeout(function () { load(1); }, 250);
      }
      form.addEventListener('compositionstart', function () {
        composing = true;
        window.clearTimeout(timer);
        revision++;
        if (request) request.abort();
        closeSocket();
        results.removeAttribute('aria-busy');
      });
      form.addEventListener('compositionend', function () { composing = false; schedule(); });
      form.addEventListener('input', function (event) {
        if (!composing && !event.isComposing && event.target.matches('input[type="text"]')) schedule();
      });
      form.addEventListener('change', function (event) { if (event.target.matches('select')) load(1); });
      form.addEventListener('submit', function (event) { event.preventDefault(); load(1); });
      form.querySelector('[data-rating-record-reset]').addEventListener('click', function (event) {
        event.preventDefault();
        form.querySelectorAll('input[type="text"], select').forEach(function (field) { field.value = ''; });
        load(1);
      });
      results.addEventListener('click', function (event) {
        var link = event.target.closest('[data-rating-record-page]');
        if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button) return;
        event.preventDefault();
        load(Number(link.getAttribute('data-rating-record-page')) || 1);
      });
      function stop() {
        stopped = true;
        window.clearTimeout(timer);
        if (request) request.abort();
        closeSocket();
      }
      var observer = new MutationObserver(function () {
        if (!form.isConnected) { stop(); observer.disconnect(); }
      });
      observer.observe(document.body, { childList: true, subtree: true });
      window.addEventListener('pagehide', stop);
      window.addEventListener('pageshow', function () {
        if (!stopped || !form.isConnected) return;
        stopped = false;
        connectSocket(form.getAttribute('data-record-socket'));
      });
      connectSocket(form.getAttribute('data-record-socket'));
    });
  }

  function decorateBoardFilterFields(root) {
    var fields = Array.from((root || document).querySelectorAll('.rating-board-filter-field'));
    if (root && root.closest) {
      var field = root.closest('.rating-board-filter-field');
      if (field) fields.push(field);
    }
    fields.forEach(function (field) {
      var original = field.querySelector('input.textbox[name]');
      var input = field.querySelector('.autocomplete-wrapper input');
      if (!original || !input) return;
      ['placeholder', 'aria-label'].forEach(function (attribute) {
        var value = original.getAttribute(attribute);
        if (value !== null && input.getAttribute(attribute) !== value) input.setAttribute(attribute, value);
      });
    });
  }

  function bindProblemRatingFilters(root) {
    (root || document).querySelectorAll('[data-problem-rating-filter]').forEach(function (select) {
      if (select.hasAttribute('data-rating-bound')) return;
      select.setAttribute('data-rating-bound', '1');
      var form = document.querySelector('#searchForm');
      var search = form && form.querySelector('[name="q"]');
      if (!search) return;
      select.addEventListener('change', function () {
        var query = search.value.replace(/(^|\s)difficulty:(?:"[^"]*"|'[^']*'|[^\s]+)/g, ' ').trim();
        search.value = query + (select.value ? (query ? ' ' : '') + 'difficulty:' + select.value : '');
        if (form.requestSubmit) form.requestSubmit();
        else form.submit();
      });
    });
  }

  function initialize(root) {
    decorate(root);
    bindDialogs(root);
    bindSettledEditForms(root);
    bindCountdowns(root);
    bindBoardTables(root);
    bindBoardFilters(root);
    bindRecordFilters(root);
    bindProblemRatingFilters(root);
    decorateBoardFilterFields(root);
  }

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    var dialog = document.querySelector('.rating-page-dialog.is-open');
    if (dialog) setDialogOpen(dialog, false);
  });

  document.addEventListener('DOMContentLoaded', function () {
    initialize(document);
    new MutationObserver(function (records) {
      records.forEach(function (record) {
        record.addedNodes.forEach(function (node) {
          if (node.nodeType === 1) initialize(node);
        });
      });
    }).observe(document.body, { childList: true, subtree: true });
  });
}());
