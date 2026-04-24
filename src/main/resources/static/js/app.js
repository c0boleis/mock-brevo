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

  // Which accounts currently have their drilldown row expanded (key = apiKey, value = 'campaigns' | 'lists' | null)
  const accountDrilldown = new Map();

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
      const apiKeyAttr = a.apiKey ? esc(a.apiKey) : '';
      const counterItems = [
        { k: 'emailsSent', label: 'emailsSent' },
        { k: 'contacts',   label: 'contacts' },
        { k: 'lists',      label: 'lists',     drill: 'lists' },
        { k: 'campaigns',  label: 'campaigns', drill: 'campaigns' },
        { k: 'templates',  label: 'templates' },
        { k: 'folders',    label: 'folders' },
        { k: 'senders',    label: 'senders' },
      ];
      const cells = counterItems.map(item => {
        const v = c[item.k] || 0;
        const zero = v === 0 ? ' zero' : '';
        if (item.drill && a.apiKey && v > 0) {
          const openNow = accountDrilldown.get(a.apiKey) === item.drill;
          return `<button type="button" class="counter counter-drill${zero}${openNow ? ' open' : ''}"
            data-drill="${item.drill}" data-api-key="${apiKeyAttr}"
            title="Afficher ${item.label}">${item.label}:${v}</button>`;
        }
        return `<span class="counter${zero}">${item.label}:${v}</span>`;
      }).join('');
      const key = a.apiKey
        ? `<code>${esc(a.apiKey)}</code>`
        : `<code>${esc(a.apiKeyPreview)}</code> <span class="muted">masquée</span>`;
      const acct = a.account || {};
      const actions = a.apiKey
        ? `<button class="btn-action" data-new-campaign-for="${apiKeyAttr}" title="Créer une nouvelle campagne">+ Campagne</button>`
        : `<span class="muted">clé masquée</span>`;
      const drill = a.apiKey ? accountDrilldown.get(a.apiKey) : null;
      const drillRow = drill
        ? `<tr class="drill-row" data-for="${apiKeyAttr}"><td colspan="6">
            <div class="drill-wrap" data-api-key="${apiKeyAttr}" data-kind="${drill}">
              <div class="muted" style="padding:12px">Chargement…</div>
            </div>
          </td></tr>`
        : '';
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
        </tr>${drillRow}`;
    }).join('');

    tbody.querySelectorAll('[data-new-campaign-for]').forEach(btn => {
      btn.addEventListener('click', () => openCampaignModal(btn.dataset.newCampaignFor));
    });

    tbody.querySelectorAll('.counter-drill').forEach(btn => {
      btn.addEventListener('click', () => toggleAccountDrill(btn.dataset.apiKey, btn.dataset.drill));
    });

    // Populate any drill rows still showing "Chargement…"
    tbody.querySelectorAll('.drill-wrap').forEach(el => {
      if (!el.dataset.loaded) {
        loadDrilldown(el.dataset.apiKey, el.dataset.kind, el);
      }
    });
  };

  const toggleAccountDrill = (apiKey, kind) => {
    const current = accountDrilldown.get(apiKey);
    if (current === kind) {
      accountDrilldown.delete(apiKey);
    } else {
      accountDrilldown.set(apiKey, kind);
    }
    refresh();
  };

  const loadDrilldown = async (apiKey, kind, container) => {
    container.dataset.loaded = '1';
    const url = '/mock-status/accounts/' + encodeURIComponent(apiKey) + '/' + kind;
    try {
      const data = await fetchJson(url);
      if (kind === 'campaigns') {
        container.innerHTML = renderAccountCampaigns(data);
      } else if (kind === 'lists') {
        container.innerHTML = renderAccountLists(data);
      }
    } catch (e) {
      container.innerHTML = `<div class="muted" style="padding:12px;color:var(--err)">Erreur: ${esc(e.message)}</div>`;
    }
  };

  const renderAccountCampaigns = (data) => {
    const items = data.campaigns || [];
    if (!items.length) {
      return '<div class="empty" style="padding:16px">Aucune campagne.</div>';
    }
    const rows = items.map(c => `
      <tr>
        <td class="mono muted">#${esc(c.id)}</td>
        <td><a href="/marketing-campaign/edit/${esc(c.id)}" class="deep-link">${esc(c.name || '(sans nom)')}</a></td>
        <td class="muted">${esc(c.subject || '')}</td>
        <td><span class="badge status-${esc(c.status || 'draft')}">${esc(c.status || 'draft')}</span></td>
        <td class="mono muted">${c.deliveredCount != null ? esc(c.deliveredCount) : '—'}</td>
        <td class="mono muted">${esc(c.utmCampaign || '')}</td>
        <td class="mono muted" title="${esc(c.sentDate || '')}">${c.sentDate ? fmtRelative(c.sentDate) : '<span class="muted">jamais envoyée</span>'}</td>
      </tr>
    `).join('');
    return `
      <div class="drill-inner">
        <div class="drill-title">Campagnes (${esc(data.count)}) — cliquer sur le nom pour ouvrir la vue détail</div>
        <table>
          <thead>
            <tr><th>ID</th><th>Nom</th><th>Sujet</th><th>Statut</th><th>Delivered</th><th>UTM</th><th>Envoyée</th></tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>`;
  };

  const renderAccountLists = (data) => {
    const items = data.lists || [];
    if (!items.length) {
      return '<div class="empty" style="padding:16px">Aucune liste.</div>';
    }
    const rows = items.map(l => `
      <tr>
        <td class="mono muted">#${esc(l.id)}</td>
        <td><a href="/contact/list/id/${esc(l.id)}" class="deep-link">${esc(l.name || '(sans nom)')}</a></td>
        <td class="mono muted">${esc(l.uniqueSubscribers)}</td>
        <td class="muted">${l.folderName ? esc(l.folderName) + ' <span class="muted">#' + esc(l.folderId) + '</span>' : '<span class="muted">—</span>'}</td>
      </tr>
    `).join('');
    return `
      <div class="drill-inner">
        <div class="drill-title">Listes (${esc(data.count)}) — cliquer sur le nom pour ouvrir la vue détail</div>
        <table>
          <thead>
            <tr><th>ID</th><th>Nom</th><th>Contacts</th><th>Dossier</th></tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>`;
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
  // DEEP-LINK VIEW (Brevo-shaped URLs: /marketing-campaign/edit/X, /contact/list/id/X)
  // ============================================================
  const deepLinkView = $('#deepLinkView');
  const deepLinkTitle = $('#deepLinkTitle');
  const deepLinkBody = $('#deepLinkBody');

  const renderCampaignDeep = (c) => {
    const statusCls = 'status-' + (c.status || 'draft').toLowerCase();
    const recipientIds = (c.recipientListIdsCsv || '')
      .split(',').map(s => s.trim()).filter(Boolean);
    const recipientLinks = recipientIds.length
      ? recipientIds.map(id => `<a href="/contact/list/id/${esc(id)}">${esc(id)}</a>`).join(', ')
      : '<span class="muted">—</span>';
    const deliveredValue = c.deliveredCount != null ? c.deliveredCount : 0;
    const apiKeyDisplay = c.account.apiKey
      ? `<code>${esc(c.account.apiKey)}</code>`
      : `<code>${esc(c.account.apiKeyPreview)}</code> <span class="muted">masquée</span>`;

    return `
      <div class="deep-card">
        <h2>
          <span>${esc(c.name)}</span>
          <span class="id">#${esc(c.id)}</span>
          <span class="badge ${statusCls}">${esc(c.status || 'draft')}</span>
        </h2>
        <div class="subtitle">${esc(c.subject || '(pas de sujet)')}</div>
        <div class="kv">
          <div class="k">Compte</div>
          <div class="v">${apiKeyDisplay} · ${esc(c.account.firstName || '')} ${esc(c.account.lastName || '')} · ${esc(c.account.email)}</div>
          <div class="k">Expéditeur</div>
          <div class="v">${esc(c.senderName || '—')} &lt;${esc(c.senderEmail || '—')}&gt;</div>
          <div class="k">Reply-to</div>
          <div class="v">${esc(c.replyTo || '—')}</div>
          <div class="k">Template</div>
          <div class="v">${c.templateId ? '#' + esc(c.templateId) : '<span class="muted">aucun</span>'}</div>
          <div class="k">UTM</div>
          <div class="v">${esc(c.utmCampaign || '—')}</div>
          <div class="k">Listes cibles</div>
          <div class="v">${recipientLinks}</div>
          <div class="k">Créée</div>
          <div class="v">${esc(c.createdAt || '—')}</div>
          <div class="k">Envoyée</div>
          <div class="v">${c.sentDate ? esc(c.sentDate) : '<span class="muted">jamais</span>'}</div>
        </div>
        <div class="section-title">Statistiques</div>
        <div class="stats-grid">
          <div class="stat"><div class="stat-label">Delivered</div><div class="stat-value">${deliveredValue}</div></div>
          <div class="stat"><div class="stat-label">Status</div><div class="stat-value">${esc(c.status || 'draft')}</div></div>
        </div>
        <div class="section-title">Liens utiles</div>
        <div>
          <a class="btn-action" href="/" title="Accueil">UI principale</a>
          <a class="btn-action" href="/mock-status" target="_blank" rel="noopener">/mock-status</a>
          <a class="btn-action doc-link" href="https://developers.brevo.com/reference/getemailcampaign" target="_blank" rel="noopener">Documentation Brevo ↗</a>
        </div>
      </div>`;
  };

  const renderListDeep = (l) => {
    const apiKeyDisplay = l.account.apiKey
      ? `<code>${esc(l.account.apiKey)}</code>`
      : `<code>${esc(l.account.apiKeyPreview)}</code> <span class="muted">masquée</span>`;
    const folderInfo = l.folderId
      ? `#${esc(l.folderId)} — ${esc(l.folderName || '')}`
      : '<span class="muted">aucun</span>';
    const contactRows = (l.contacts || []).map(c => `
      <tr>
        <td class="mono muted">#${esc(c.id)}</td>
        <td class="mono">${esc(c.email)}</td>
        <td>${esc(c.firstName || '')} ${esc(c.lastName || '')}</td>
        <td>${c.emailBlacklisted ? '<span class="badge" style="color:var(--err);border-color:var(--err)">blacklisted</span>' : ''}</td>
      </tr>
    `).join('');

    return `
      <div class="deep-card">
        <h2>
          <span>${esc(l.name)}</span>
          <span class="id">#${esc(l.id)}</span>
        </h2>
        <div class="subtitle">${esc(l.contactsCount)} contact(s)</div>
        <div class="kv">
          <div class="k">Compte</div>
          <div class="v">${apiKeyDisplay} · ${esc(l.account.firstName || '')} ${esc(l.account.lastName || '')} · ${esc(l.account.email)}</div>
          <div class="k">Dossier</div>
          <div class="v">${folderInfo}</div>
        </div>
        <div class="section-title">Contacts (${esc(l.contactsCount)})</div>
        ${contactRows ? `
          <table>
            <thead>
              <tr><th style="width:60px">ID</th><th>Email</th><th>Nom</th><th style="width:120px"></th></tr>
            </thead>
            <tbody>${contactRows}</tbody>
          </table>
        ` : '<div class="empty">Liste vide.</div>'}
        <div class="section-title">Liens utiles</div>
        <div>
          <a class="btn-action" href="/" title="Accueil">UI principale</a>
          <a class="btn-action doc-link" href="https://developers.brevo.com/reference/getlist" target="_blank" rel="noopener">Documentation Brevo ↗</a>
        </div>
      </div>`;
  };

  const renderNotFound = (what, id) => `
    <div class="deep-not-found">
      <strong>${esc(what)} #${esc(id)} introuvable</strong>
      <div style="margin-top:8px">Ce compte n'a peut-être pas été provisionné dans ce mock, ou l'ID est périmé.</div>
      <div style="margin-top:12px"><a class="btn-action btn-primary" href="/">Retour à l'accueil</a></div>
    </div>`;

  const handleDeepLink = async () => {
    const path = window.location.pathname;
    let match = path.match(/^\/marketing-campaign\/edit\/(\d+)\/?$/);
    if (match) {
      const id = match[1];
      deepLinkTitle.textContent = `Campagne #${id}`;
      deepLinkBody.innerHTML = '<div class="deep-card muted">Chargement…</div>';
      deepLinkView.style.display = 'block';
      // hide default tabs to focus on the detail view
      document.querySelectorAll('section#accounts, section#requests, nav.tabs').forEach(el => el.style.display = 'none');
      try {
        const resp = await fetch('/mock-status/campaigns/' + id, { headers: { accept: 'application/json' } });
        if (resp.status === 404) {
          deepLinkBody.innerHTML = renderNotFound('Campagne', id);
          return;
        }
        if (!resp.ok) throw new Error(resp.status + ' ' + resp.statusText);
        const data = await resp.json();
        deepLinkTitle.innerHTML = `Campagne — <code>${esc(data.name || ('#' + data.id))}</code>`;
        deepLinkBody.innerHTML = renderCampaignDeep(data);
      } catch (e) {
        deepLinkBody.innerHTML = `<div class="deep-not-found"><strong>Erreur</strong><div>${esc(e.message)}</div></div>`;
      }
      return true;
    }
    match = path.match(/^\/contact\/list\/id\/(\d+)\/?$/);
    if (match) {
      const id = match[1];
      deepLinkTitle.textContent = `Liste de contacts #${id}`;
      deepLinkBody.innerHTML = '<div class="deep-card muted">Chargement…</div>';
      deepLinkView.style.display = 'block';
      document.querySelectorAll('section#accounts, section#requests, nav.tabs').forEach(el => el.style.display = 'none');
      try {
        const resp = await fetch('/mock-status/lists/' + id, { headers: { accept: 'application/json' } });
        if (resp.status === 404) {
          deepLinkBody.innerHTML = renderNotFound('Liste', id);
          return;
        }
        if (!resp.ok) throw new Error(resp.status + ' ' + resp.statusText);
        const data = await resp.json();
        deepLinkTitle.innerHTML = `Liste — <code>${esc(data.name || ('#' + data.id))}</code>`;
        deepLinkBody.innerHTML = renderListDeep(data);
      } catch (e) {
        deepLinkBody.innerHTML = `<div class="deep-not-found"><strong>Erreur</strong><div>${esc(e.message)}</div></div>`;
      }
      return true;
    }
    return false;
  };

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

  // If we're on a deep-link URL, render the detail view and skip the default UI polling.
  handleDeepLink().then(isDeep => {
    if (!isDeep) {
      refresh();
      schedule();
    }
  });
})();
