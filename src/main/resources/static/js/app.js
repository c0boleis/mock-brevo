(() => {
  'use strict';

  const $ = (s) => document.querySelector(s);
  const $$ = (s) => document.querySelectorAll(s);

  // ============================================================
  // THEME (dark / light) — bootstrap script in <head> already set
  // `data-theme` on <html> to avoid flash of wrong theme.
  // ============================================================
  const THEME_KEY = 'mock-brevo-theme';
  const getTheme = () => document.documentElement.getAttribute('data-theme') || 'dark';

  const applyTheme = (theme) => {
    document.documentElement.setAttribute('data-theme', theme);
    const ic = $('#themeToggle .ic');
    const label = $('#themeLabel');
    if (ic) ic.textContent = theme === 'light' ? '☀️' : '🌙';
    if (label) label.textContent = theme === 'light' ? 'clair' : 'sombre';
    // Monaco has a global theme; switching it updates all live editors
    if (window.__monacoReady) {
      window.__monacoReady.then(m => {
        if (m) {
          try { m.editor.setTheme(theme === 'light' ? 'vs' : 'vs-dark'); } catch (e) {}
        }
      });
    }
  };

  $('#themeToggle')?.addEventListener('click', () => {
    const next = getTheme() === 'light' ? 'dark' : 'light';
    try { localStorage.setItem(THEME_KEY, next); } catch (e) {}
    applyTheme(next);
  });

  // sync label/icon + Monaco now that we're loaded
  applyTheme(getTheme());

  // ============================================================
  // STATE
  // ============================================================
  const tabs = { accounts: $('#accounts'), requests: $('#requests') };
  let activeTab = 'accounts';

  const openDetails = new Set();
  const detailCache = new Map();
  const monacoEditors = new Map();      // id -> [editor, editor]
  const monacoViewStates = new Map();   // `${id}_${which}` -> saved view state
  let lastRequestsKey = null;

  // ============================================================
  // UTILITIES
  // ============================================================
  const fmtRelative = (isoStr) => {
    if (!isoStr) return '';
    const t = new Date(isoStr).getTime();
    const d = Date.now() - t;
    if (d < 1500) return "à l'instant";
    if (d < 60_000) return Math.round(d / 1000) + ' s';
    if (d < 3_600_000) return Math.round(d / 60_000) + ' min';
    if (d < 86_400_000) return Math.round(d / 3_600_000) + ' h';
    return Math.round(d / 86_400_000) + ' j';
  };

  const fmtTime = (isoStr) => {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      + '.' + String(d.getMilliseconds()).padStart(3, '0');
  };

  const fmtBytes = (n) => {
    if (n == null) return '';
    if (n < 1024) return n + ' B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KiB';
    return (n / 1024 / 1024).toFixed(1) + ' MiB';
  };

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[c]);

  const statusClass = (s) => {
    if (!s) return '';
    if (s < 300) return 'status-2xx';
    if (s < 400) return 'status-3xx';
    if (s < 500) return 'status-4xx';
    return 'status-5xx';
  };

  const prettifyJson = (text) => {
    if (!text) return text;
    const trimmed = text.trim();
    if (!trimmed || (trimmed[0] !== '{' && trimmed[0] !== '[')) return text;
    try { return JSON.stringify(JSON.parse(trimmed), null, 2); } catch { return text; }
  };

  const isJson = (ct) => ct && ct.toLowerCase().includes('json');

  const fetchJson = async (url) => {
    const r = await fetch(url, { headers: { 'accept': 'application/json' } });
    if (!r.ok) throw new Error(r.status + ' ' + r.statusText);
    return await r.json();
  };

  // ============================================================
  // BREVO DOC ROUTES — method+path pattern → developers.brevo.com slug
  // ============================================================
  const DOC_ROUTES = [
    ['GET',    '/v3/account',                              'getaccount'],
    ['GET',    '/v3/senders',                              'getsenders'],
    ['POST',   '/v3/smtp/email',                           'sendtransacemail'],
    ['GET',    '/v3/smtp/emails',                          'getemaileventreport'],
    ['GET',    '/v3/smtp/templates',                       'getsmtptemplates'],
    ['POST',   '/v3/smtp/templates',                       'createsmtptemplate'],
    ['GET',    '/v3/smtp/templates/*',                     'getsmtptemplate'],
    ['PUT',    '/v3/smtp/templates/*',                     'updatesmtptemplate'],
    ['POST',   '/v3/smtp/templates/*/sendTemplate',        'sendtemplate'],
    ['GET',    '/v3/contacts',                             'getcontacts'],
    ['POST',   '/v3/contacts',                             'createcontact'],
    ['GET',    '/v3/contacts/*',                           'getcontactinfo'],
    ['PUT',    '/v3/contacts/*',                           'updatecontact'],
    ['DELETE', '/v3/contacts/*',                           'deletecontact'],
    ['GET',    '/v3/contacts/lists',                       'getlists'],
    ['POST',   '/v3/contacts/lists',                       'createlist'],
    ['GET',    '/v3/contacts/lists/*',                     'getlist'],
    ['PUT',    '/v3/contacts/lists/*',                     'updatelist'],
    ['DELETE', '/v3/contacts/lists/*',                     'deletelist'],
    ['GET',    '/v3/contacts/lists/*/contacts',            'getcontactsfromlist'],
    ['POST',   '/v3/contacts/lists/*/contacts/add',        'addcontacttolist'],
    ['POST',   '/v3/contacts/lists/*/contacts/remove',     'removecontactfromlist'],
    ['DELETE', '/v3/contacts/lists/*/contacts',            'removecontactfromlist'],
    ['POST',   '/v3/contacts/import',                      'importcontacts'],
    ['POST',   '/v3/contacts/export',                      'requestcontactexport'],
    ['GET',    '/v3/contacts/folders',                     'getfolders'],
    ['POST',   '/v3/contacts/folders',                     'createfolder'],
    ['GET',    '/v3/contacts/folders/*',                   'getfolder'],
    ['PUT',    '/v3/contacts/folders/*',                   'updatefolder'],
    ['DELETE', '/v3/contacts/folders/*',                   'deletefolder'],
    ['GET',    '/v3/contacts/folders/*/lists',             'getfolderlists'],
    ['GET',    '/v3/emailCampaigns',                       'getemailcampaigns'],
    ['POST',   '/v3/emailCampaigns',                       'createemailcampaign'],
    ['GET',    '/v3/emailCampaigns/*',                     'getemailcampaign'],
    ['PUT',    '/v3/emailCampaigns/*',                     'updateemailcampaign'],
    ['DELETE', '/v3/emailCampaigns/*',                     'deleteemailcampaign'],
    ['POST',   '/v3/emailCampaigns/*/sendNow',             'sendemailcampaignnow'],
    ['POST',   '/v3/emailCampaigns/*/sendTest',            'sendtestemail'],
    ['GET',    '/v3/webhooks',                             'getwebhooks'],
    ['POST',   '/v3/webhooks',                             'createwebhook'],
  ];

  const matchPattern = (pattern, path) => {
    const ps = pattern.split('/');
    const xs = path.split('/');
    if (ps.length !== xs.length) return false;
    for (let i = 0; i < ps.length; i++) {
      if (ps[i] === '*') continue;
      if (ps[i] !== xs[i]) return false;
    }
    return true;
  };

  const docUrlFor = (method, path) => {
    for (const [m, p, slug] of DOC_ROUTES) {
      if (m === method && matchPattern(p, path)) {
        return 'https://developers.brevo.com/reference/' + slug;
      }
    }
    return null;
  };

  // ============================================================
  // MONACO EDITOR LIFECYCLE
  // ============================================================
  const saveViewStateFor = (id) => {
    const list = monacoEditors.get(id);
    if (!list) return;
    list.forEach(ed => {
      try {
        const which = ed.__which;
        const state = ed.saveViewState();
        if (which && state) monacoViewStates.set(`${id}_${which}`, state);
      } catch (e) {}
    });
  };

  const disposeEditorsFor = (id) => {
    const list = monacoEditors.get(id);
    if (!list) return;
    saveViewStateFor(id);
    list.forEach(ed => { try { ed && ed.dispose(); } catch (e) {} });
    monacoEditors.delete(id);
  };

  const disposeAllEditors = () => {
    for (const id of Array.from(monacoEditors.keys())) disposeEditorsFor(id);
  };

  const mountEditorsFor = async (entry) => {
    const monaco = await window.__monacoReady;
    const containers = document.querySelectorAll(`.monaco-body[data-monaco-id="${entry.id}"]`);
    if (!containers.length) return;
    if (!monaco) {
      // fallback: plain <pre>
      containers.forEach(el => {
        const which = el.dataset.monacoWhich;
        const raw = which === 'req' ? entry.requestBody : entry.responseBody;
        const ct = which === 'req'
          ? (entry.requestHeaders || {})['content-type']
          : (entry.responseHeaders || {})['content-type'];
        const pretty = isJson(ct) ? prettifyJson(raw) : (raw || '');
        const pre = document.createElement('pre');
        pre.textContent = pretty;
        el.replaceWith(pre);
      });
      return;
    }
    const list = [];
    containers.forEach(el => {
      if (el.dataset.mounted === '1') return;
      const which = el.dataset.monacoWhich;
      const raw = which === 'req' ? entry.requestBody : entry.responseBody;
      const ct = which === 'req'
        ? (entry.requestHeaders || {})['content-type']
        : (entry.responseHeaders || {})['content-type'];
      const value = isJson(ct) ? prettifyJson(raw) : (raw || '');
      const lang = isJson(ct) ? 'json' : 'plaintext';
      el.textContent = '';
      el.classList.remove('pending');
      const editor = monaco.editor.create(el, {
        value,
        language: lang,
        readOnly: true,
        theme: getTheme() === 'light' ? 'vs' : 'vs-dark',
        minimap: { enabled: false },
        folding: true,
        showFoldingControls: 'always',
        fontSize: 12,
        lineNumbers: 'on',
        wordWrap: 'off',
        scrollBeyondLastLine: false,
        automaticLayout: true,
        renderLineHighlight: 'none',
        scrollbar: { alwaysConsumeMouseWheel: false }
      });
      editor.__which = which;
      const saved = monacoViewStates.get(`${entry.id}_${which}`);
      if (saved) {
        try { editor.restoreViewState(saved); } catch (e) {}
      } else if (lang === 'json') {
        setTimeout(() => {
          try { editor.getAction('editor.foldLevel2')?.run(); } catch (e) {}
        }, 150);
      }
      el.dataset.mounted = '1';
      list.push(editor);
    });
    if (list.length) {
      const existing = monacoEditors.get(entry.id) || [];
      monacoEditors.set(entry.id, existing.concat(list));
    }
  };

  // ============================================================
  // RENDERERS — Accounts tab
  // ============================================================
  const filterEl = $('#filter');
  const matchesFilter = (haystacks) => {
    const q = (filterEl.value || '').trim().toLowerCase();
    if (!q) return true;
    return haystacks.some(h => String(h || '').toLowerCase().includes(q));
  };

  const renderAccounts = (data) => {
    const tbody = $('#accountsBody');
    const rows = (data.accounts || []).filter(a =>
      matchesFilter([a.apiKey, a.apiKeyPreview, a.account?.email]));
    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty">Aucun compte — envoyez un appel pour provisionner.</td></tr>';
      return;
    }
    tbody.innerHTML = rows.map(a => {
      const c = a.counters || {};
      const cells = ['emailsSent', 'contacts', 'lists', 'campaigns', 'templates', 'folders', 'senders']
        .map(k => {
          const v = c[k] || 0;
          return `<span class="counter ${v === 0 ? 'zero' : ''}">${k}:${v}</span>`;
        }).join('');
      const key = a.apiKey
        ? `<code>${esc(a.apiKey)}</code>`
        : `<code>${esc(a.apiKeyPreview)}</code> <span class="muted">masquée</span>`;
      const acct = a.account || {};
      const apiKeyAttr = a.apiKey ? esc(a.apiKey) : '';
      const actions = a.apiKey
        ? `<button class="btn-action" data-new-campaign-for="${apiKeyAttr}" title="Créer une nouvelle campagne">+ Campagne</button>`
        : `<span class="muted">clé masquée</span>`;
      return `
        <tr>
          <td>${key}</td>
          <td>
            <div>${esc(acct.firstName || '')} ${esc(acct.lastName || '')}</div>
            <div class="muted mono">${esc(acct.email || '')}</div>
          </td>
          <td><div class="counters">${cells}</div></td>
          <td class="mono muted" title="${esc(a.createdAt)}">${fmtRelative(a.createdAt)}</td>
          <td class="mono muted" title="${esc(a.lastSeenAt)}">${fmtRelative(a.lastSeenAt)}</td>
          <td>${actions}</td>
        </tr>`;
    }).join('');

    tbody.querySelectorAll('[data-new-campaign-for]').forEach(btn => {
      btn.addEventListener('click', () => openCampaignModal(btn.dataset.newCampaignFor));
    });
  };

  // ============================================================
  // RENDERERS — Requests tab
  // ============================================================
  const renderHeaders = (h) => {
    if (!h || !Object.keys(h).length) return '<div class="empty-body">Aucun header.</div>';
    return `<div class="headers">` +
      Object.entries(h).map(([k, v]) =>
        `<div class="hk">${esc(k)}</div><div class="hv">${esc(v)}</div>`).join('') +
      `</div>`;
  };

  const renderBody = (text, truncated, contentType, entryId, which) => {
    if (text == null || text === '') return '<div class="empty-body">Aucun body.</div>';
    const tag = truncated ? `<span class="truncated">tronqué à 16 KiB</span>` : '';
    const actions = `
      <div class="body-actions">
        ${tag}
        <button type="button" class="copy-btn" data-copy-id="${entryId}" data-copy-which="${which}" title="Copier dans le presse-papier">Copier</button>
      </div>`;
    if (isJson(contentType)) {
      const pretty = prettifyJson(text);
      const lineCount = (pretty.match(/\n/g) || []).length + 1;
      const sizeClass = lineCount <= 6 ? ' small' : '';
      return `
        <div class="body-wrap">
          ${actions}
          <div class="monaco-body pending${sizeClass}" data-monaco-id="${entryId}" data-monaco-which="${which}">Chargement de l'éditeur…</div>
        </div>`;
    }
    return `
      <div class="body-wrap">
        ${actions}
        <pre>${esc(text)}</pre>
      </div>`;
  };

  const renderDetail = (entry) => {
    const reqCt = entry.requestHeaders && entry.requestHeaders['content-type'];
    const respCt = entry.responseHeaders && entry.responseHeaders['content-type'];
    const docUrl = docUrlFor(entry.method, entry.path);
    const docLink = docUrl ? `<a class="doc-link" href="${docUrl}" target="_blank" rel="noopener" title="Documentation Brevo">doc ↗</a>` : '';
    return `
      <div class="detail-inner">
        <div class="block">
          <h3>Requête <span class="tag">${esc(entry.method)} ${esc(entry.path)}</span>${docLink}</h3>
          <h4>Headers</h4>
          ${renderHeaders(entry.requestHeaders)}
          <h4>Body${entry.requestSize != null ? ' <span class="muted">(' + fmtBytes(entry.requestSize) + ')</span>' : ''}</h4>
          ${renderBody(entry.requestBody, entry.requestTruncated, reqCt, entry.id, 'req')}
        </div>
        <div class="block">
          <h3>Réponse <span class="tag"><span class="${statusClass(entry.status)}">${esc(entry.status ?? '—')}</span></span></h3>
          <h4>Headers</h4>
          ${renderHeaders(entry.responseHeaders)}
          <h4>Body${entry.responseSize != null ? ' <span class="muted">(' + fmtBytes(entry.responseSize) + ')</span>' : ''}</h4>
          ${renderBody(entry.responseBody, entry.responseTruncated, respCt, entry.id, 'resp')}
        </div>
      </div>`;
  };

  const renderRequests = (data) => {
    const tbody = $('#requestsBody');
    const rows = (data.requests || []).filter(r =>
      matchesFilter([r.path, r.apiKeyPreview, r.method, String(r.status)]));
    const key = rows.map(r => r.id).join(',');
    if (key === lastRequestsKey) return;
    lastRequestsKey = key;
    disposeAllEditors();
    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="7" class="empty">Aucun appel capté. Envoyez une requête vers <code>/v3/…</code>.</td></tr>';
      return;
    }
    tbody.innerHTML = rows.map(r => {
      const q = r.query ? `<span class="muted">?${esc(r.query)}</span>` : '';
      const isOpen = openDetails.has(r.id);
      const reqB = r.requestSize != null ? fmtBytes(r.requestSize) : '';
      const resB = r.responseSize != null ? fmtBytes(r.responseSize) : '';
      const sizes = [reqB, resB].filter(Boolean).join(' / ') || '—';
      const cached = detailCache.get(r.id);
      const detailHtml = isOpen
        ? (cached
            ? renderDetail(cached)
            : `<div class="detail-inner"><div class="block muted">Chargement du détail…</div></div>`)
        : '';
      const docUrl = docUrlFor(r.method, r.path);
      const docLink = docUrl
        ? `<a class="doc-link" href="${docUrl}" target="_blank" rel="noopener" title="Documentation Brevo" onclick="event.stopPropagation()">↗</a>`
        : '';
      return `
        <tr class="summary ${isOpen ? 'open' : ''}" data-id="${r.id}">
          <td class="mono" title="${esc(r.at)}"><span class="chev">▸</span>${fmtTime(r.at)}</td>
          <td><span class="pill method-${esc(r.method)}">${esc(r.method)}</span></td>
          <td class="mono">${esc(r.path)}${q}${docLink}</td>
          <td class="mono muted">${esc(r.apiKeyPreview || '—')}</td>
          <td class="${statusClass(r.status)} mono">${esc(r.status ?? '—')}</td>
          <td class="mono muted">${esc(r.durationMs)} ms</td>
          <td class="mono muted">${sizes}</td>
        </tr>
        <tr class="detail" data-id="${r.id}" style="${isOpen ? '' : 'display:none'}">
          <td colspan="7">${detailHtml}</td>
        </tr>`;
    }).join('');

    tbody.querySelectorAll('tr.summary').forEach(tr => {
      tr.addEventListener('click', () => toggleDetail(Number(tr.dataset.id), tr));
    });
    for (const id of openDetails) {
      const entry = detailCache.get(id);
      if (entry) mountEditorsFor(entry);
    }
  };

  const toggleDetail = async (id, summaryRow) => {
    const detailRow = summaryRow.nextElementSibling;
    if (openDetails.has(id)) {
      openDetails.delete(id);
      summaryRow.classList.remove('open');
      detailRow.style.display = 'none';
      disposeEditorsFor(id);
      return;
    }
    openDetails.add(id);
    summaryRow.classList.add('open');
    detailRow.style.display = '';
    if (!detailCache.has(id)) {
      detailRow.querySelector('td').innerHTML = `<div class="detail-inner"><div class="block muted">Chargement…</div></div>`;
      try {
        const entry = await fetchJson('/mock-status/requests/' + id);
        detailCache.set(id, entry);
        if (detailCache.size > 50) {
          const first = detailCache.keys().next().value;
          detailCache.delete(first);
        }
        detailRow.querySelector('td').innerHTML = renderDetail(entry);
        mountEditorsFor(entry);
      } catch (e) {
        detailRow.querySelector('td').innerHTML =
          `<div class="detail-inner"><div class="block" style="color:var(--err)">Erreur: ${esc(e.message)}</div></div>`;
      }
    } else {
      detailRow.querySelector('td').innerHTML = renderDetail(detailCache.get(id));
      mountEditorsFor(detailCache.get(id));
    }
  };

  // ============================================================
  // TABS + REFRESH
  // ============================================================
  $$('nav.tabs button[data-tab]').forEach((btn) => {
    btn.addEventListener('click', () => {
      $$('nav.tabs button[data-tab]').forEach(b => b.classList.toggle('active', b === btn));
      Object.entries(tabs).forEach(([k, el]) => el.classList.toggle('active', k === btn.dataset.tab));
      activeTab = btn.dataset.tab;
      refresh();
    });
  });

  const setHealth = (ok, msg) => {
    $('#healthDot').classList.toggle('stale', !ok);
    $('#healthText').textContent = msg;
  };

  const refresh = async () => {
    try {
      if (activeTab === 'accounts') {
        const d = await fetchJson('/mock-status');
        renderAccounts(d);
        setHealth(true, `${d.accountsCount} compte(s) · ${fmtTime(new Date().toISOString())}`);
      } else {
        const q = filterEl.value.trim();
        const url = '/mock-status/requests?limit=200' + (q ? '&apiKey=' + encodeURIComponent(q) : '');
        const d = await fetchJson(url);
        renderRequests(d);
        setHealth(true, `${d.total} en buffer · ${fmtTime(new Date().toISOString())}`);
      }
    } catch (e) {
      setHealth(false, 'erreur: ' + e.message);
    }
  };

  // ============================================================
  // COPY TO CLIPBOARD
  // ============================================================
  const copyToClipboard = async (text) => {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.top = '-1000px';
    document.body.appendChild(ta);
    ta.select();
    try {
      const ok = document.execCommand('copy');
      if (!ok) throw new Error('execCommand copy failed');
    } finally {
      document.body.removeChild(ta);
    }
  };

  document.addEventListener('click', (e) => {
    const btn = e.target.closest('.copy-btn');
    if (!btn) return;
    e.stopPropagation();
    e.preventDefault();
    const id = Number(btn.dataset.copyId);
    const which = btn.dataset.copyWhich;
    const entry = detailCache.get(id);
    if (!entry) return;
    const raw = which === 'req' ? entry.requestBody : entry.responseBody;
    if (raw == null) return;
    const ct = which === 'req'
      ? (entry.requestHeaders || {})['content-type']
      : (entry.responseHeaders || {})['content-type'];
    const payload = isJson(ct) ? prettifyJson(raw) : raw;
    copyToClipboard(payload).then(() => {
      btn.classList.add('ok'); btn.textContent = 'Copié ✓';
      setTimeout(() => { btn.classList.remove('ok'); btn.textContent = 'Copier'; }, 1500);
    }).catch(() => {
      btn.classList.add('err'); btn.textContent = 'Erreur';
      setTimeout(() => { btn.classList.remove('err'); btn.textContent = 'Copier'; }, 1500);
    });
  });

  // ============================================================
  // CAMPAIGN CREATION MODAL
  // ============================================================
  let currentCampaignApiKey = null;
  const campaignModal = $('#campaign-modal');
  const campaignForm = $('#campaign-form');
  const campaignError = $('#campaign-modal-error');
  const campaignKeySpan = $('#campaign-modal-key');

  const openCampaignModal = (apiKey) => {
    currentCampaignApiKey = apiKey;
    campaignKeySpan.textContent = apiKey;
    campaignError.style.display = 'none';
    campaignForm.reset();
    campaignModal.classList.add('open');
    setTimeout(() => campaignForm.querySelector('input[name="name"]')?.focus(), 50);
  };

  const closeCampaignModal = () => {
    campaignModal.classList.remove('open');
    currentCampaignApiKey = null;
  };

  $('#campaign-cancel').addEventListener('click', closeCampaignModal);
  campaignModal.addEventListener('click', (e) => {
    if (e.target === campaignModal) closeCampaignModal();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && campaignModal.classList.contains('open')) closeCampaignModal();
  });

  campaignForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!currentCampaignApiKey) return;
    const fd = new FormData(campaignForm);
    const body = {};
    for (const [k, v] of fd.entries()) {
      if (v != null && String(v).trim() !== '') body[k] = String(v).trim();
    }
    const submitBtn = campaignForm.querySelector('button[type=submit]');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Création…';
    try {
      const resp = await fetch('/mock-status/accounts/' + encodeURIComponent(currentCampaignApiKey) + '/campaigns', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body)
      });
      if (!resp.ok) throw new Error(resp.status + ' ' + resp.statusText);
      await resp.json();
      closeCampaignModal();
      lastRequestsKey = null;
      await refresh();
    } catch (err) {
      campaignError.textContent = 'Erreur : ' + err.message;
      campaignError.style.display = 'block';
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Créer';
    }
  });

  // ============================================================
  // FILTER + AUTO-REFRESH
  // ============================================================
  filterEl.addEventListener('input', () => refresh());

  let timer = null;
  const schedule = () => {
    clearInterval(timer);
    if ($('#autoRefresh').checked) {
      timer = setInterval(refresh, 2000);
    }
  };
  $('#autoRefresh').addEventListener('change', schedule);

  refresh();
  schedule();
})();
