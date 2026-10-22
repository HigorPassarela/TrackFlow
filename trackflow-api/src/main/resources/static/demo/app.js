// Demo client for the Trackflow API. Plain browser JavaScript, no build step.
'use strict';

const $ = (selector) => document.querySelector(selector);

const state = {
    me: null,
    project: null,
    version: null,
    peaks: null,
    comments: [],
    events: null,
};

// ---------- HTTP ----------

/** Spring Security puts the CSRF token in this cookie; state-changing requests echo it in a header. */
function csrfToken() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
}

async function api(method, path, body) {
    const headers = { 'X-XSRF-TOKEN': csrfToken() };
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    const response = await fetch(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        credentials: 'same-origin',
    });
    if (response.status === 204) {
        return null;
    }
    const data = response.headers.get('Content-Type')?.includes('json') ? await response.json() : null;
    if (!response.ok) {
        const error = new Error(data?.detail || data?.title || response.statusText);
        error.status = response.status;
        error.code = data?.code;
        throw error;
    }
    return data;
}

// ---------- Session ----------

async function start() {
    // Any request makes the server issue the CSRF cookie.
    await fetch('/actuator/health', { credentials: 'same-origin' });
    try {
        state.me = await api('GET', '/api/me');
        showWorkspace();
    } catch (error) {
        if (error.status !== 401) {
            throw error;
        }
        $('#login').hidden = false;
        if (new URLSearchParams(location.search).has('login')) {
            $('#login-error').textContent = 'O login não foi concluído.';
        }
    }
}

$('#dev-login').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
        await api('POST', '/api/dev/login', { name: event.target.name.value });
        location.reload();
    } catch (error) {
        $('#login-error').textContent =
            error.status === 404 ? 'Login de desenvolvimento só existe no profile local.' : error.message;
    }
});

async function logout() {
    await api('POST', '/logout');
    location.href = '/';
}

function showWorkspace() {
    $('#login').hidden = true;
    $('#workspace').hidden = false;
    const session = $('#session');
    session.textContent = `${state.me.displayName} `;
    const exit = button('sair', logout);
    session.append(exit);
    loadProjects();
}

// ---------- Projects ----------

async function loadProjects() {
    const page = await api('GET', '/api/projects?size=50');
    const list = $('#projects');
    list.replaceChildren(...page.items.map((project) => {
        const item = element('li', `${project.title}`, 'clickable');
        item.append(badge(project.myRole));
        if (project.genre) {
            item.append(element('span', ` · ${project.genre}`, 'muted'));
        }
        if (state.project?.id === project.id) {
            item.classList.add('selected');
        }
        item.addEventListener('click', () => openProject(project));
        return item;
    }));
    if (page.items.length === 0) {
        list.replaceChildren(element('li', 'Nenhum projeto ainda.', 'muted'));
    }
}

$('#new-project').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const project = await api('POST', '/api/projects', {
        title: form.title.value,
        genre: form.genre.value || null,
    });
    form.reset();
    await loadProjects();
    openProject({ id: project.id, title: project.title });
});

async function openProject(project) {
    state.project = project;
    state.version = null;
    $('#player-card').hidden = true;
    $('#project-card').hidden = false;
    $('#project-title').textContent = project.title;
    subscribeToStatus(project.id);
    await Promise.all([loadVersions(), loadProjects()]);
}

/** Status changes arrive over SSE; the browser reconnects by itself if the connection drops. */
function subscribeToStatus(projectId) {
    state.events?.close();
    state.events = new EventSource(`/api/projects/${projectId}/events`);
    state.events.addEventListener('version-status', () => loadVersions());
}

// ---------- Versions ----------

async function loadVersions() {
    const page = await api('GET', `/api/projects/${state.project.id}/versions?size=50`);
    $('#versions').replaceChildren(...page.items.map((version) => {
        const item = element('li', `v${version.number} · ${version.originalFileName}`);
        item.append(badge(statusLabel(version.status)));
        if (version.metrics?.bpm) {
            item.append(element('span', ` · ${version.metrics.bpm} BPM (${bpmLabel(version.metrics)})`, 'muted'));
        }
        if (version.failureReason) {
            item.append(element('span', ` · ${version.failureReason}`, 'error'));
        }
        if (version.notes) {
            item.append(element('div', version.notes, 'muted'));
        }
        if (version.status === 'ready') {
            item.classList.add('clickable');
            item.addEventListener('click', () => openVersion(version));
        }
        if (state.version?.id === version.id) {
            item.classList.add('selected');
        }
        return item;
    }));
}

function statusLabel(status) {
    return {
        pending_upload: 'aguardando envio',
        uploaded: 'enviada',
        processing: 'em processamento',
        ready: 'pronta',
        failed: 'falhou',
    }[status] ?? status;
}

function bpmLabel(metrics) {
    return metrics.bpmSource === 'informed' ? 'informado' : 'estimado';
}

$('#upload').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const file = form.file.files[0];
    const als = form.als.files[0];
    const progress = $('#upload-progress');
    progress.hidden = false;
    progress.value = 0;
    try {
        const ticket = await api('POST', `/api/projects/${state.project.id}/versions/uploads`, {
            fileName: file.name,
            contentType: file.type || 'application/octet-stream',
            sizeBytes: file.size,
            ableton: als ? { fileName: als.name, sizeBytes: als.size } : null,
            notes: form.notes.value || null,
        });
        // Straight to the storage: the file never passes through the API (design D4).
        await put(ticket.upload, file, (fraction) => (progress.value = fraction * 100));
        if (als) {
            await put(ticket.abletonUpload, als, () => {});
        }
        await api('POST', `/api/projects/${state.project.id}/versions/${ticket.version.id}/confirm`);
        form.reset();
        await loadVersions();
    } catch (error) {
        alert(`Envio falhou: ${error.message}`);
    } finally {
        progress.hidden = true;
    }
});

/** XMLHttpRequest instead of fetch only because it reports upload progress. */
function put(signed, file, onProgress) {
    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open('PUT', signed.url);
        for (const [name, value] of Object.entries(signed.headers)) {
            // The browser sets these itself and refuses scripts that try.
            if (!['content-length', 'host'].includes(name.toLowerCase())) {
                request.setRequestHeader(name, value);
            }
        }
        request.upload.onprogress = (event) => onProgress(event.loaded / event.total);
        request.onload = () =>
            (request.status < 300 ? resolve() : reject(new Error(`storage answered ${request.status}`)));
        request.onerror = () => reject(new Error('storage unreachable'));
        request.send(file);
    });
}

// ---------- Player ----------

async function openVersion(version) {
    state.version = version;
    $('#player-card').hidden = false;
    $('#player-title').textContent = `v${version.number} · ${version.originalFileName}`;
    const m = version.metrics ?? {};
    $('#player-metrics').textContent = [
        m.bpm ? `${m.bpm} BPM (${bpmLabel(m)})` : 'sem BPM estimado',
        m.loudnessLufs != null ? `${m.loudnessLufs} LUFS` : null,
        m.truePeakDbtp != null ? `true peak ${m.truePeakDbtp > 0 ? '+' : ''}${m.truePeakDbtp} dBTP` : null,
    ].filter(Boolean).join(' · ');

    const playback = await api('GET', `/api/projects/${state.project.id}/versions/${version.id}/playback`);
    $('#audio').src = playback.previewUrl;
    state.peaks = playback.peaksUrl ? await (await fetch(playback.peaksUrl)).json() : null;
    await Promise.all([loadComments(), loadVersions()]);
}

function durationSeconds() {
    return (state.version?.metrics?.durationMs ?? 0) / 1000 || $('#audio').duration || 1;
}

function drawWaveform() {
    const canvas = $('#waveform');
    const width = (canvas.width = canvas.clientWidth * devicePixelRatio);
    const height = (canvas.height = 120 * devicePixelRatio);
    const context = canvas.getContext('2d');
    context.clearRect(0, 0, width, height);
    const styles = getComputedStyle(document.documentElement);
    const peaks = state.peaks?.peaks ?? [];
    const played = $('#audio').currentTime / durationSeconds();

    for (let x = 0; x < width; x++) {
        const peak = peaks[Math.floor((x / width) * peaks.length)] ?? 0;
        const bar = Math.max(1, peak * height * 0.9);
        context.fillStyle = x / width < played ? styles.getPropertyValue('--accent') : styles.getPropertyValue('--wave');
        context.fillRect(x, (height - bar) / 2, 1, bar);
    }
    // One marker per root comment; ranges get a translucent band.
    context.fillStyle = styles.getPropertyValue('--text');
    for (const comment of state.comments) {
        const start = (comment.positionMs / 1000 / durationSeconds()) * width;
        if (comment.endPositionMs) {
            const end = (comment.endPositionMs / 1000 / durationSeconds()) * width;
            context.globalAlpha = 0.15;
            context.fillRect(start, 0, end - start, height);
            context.globalAlpha = 1;
        }
        context.fillRect(start, 0, 2 * devicePixelRatio, 10 * devicePixelRatio);
    }
}

$('#waveform').addEventListener('click', (event) => {
    const canvas = event.currentTarget;
    $('#audio').currentTime = (event.offsetX / canvas.clientWidth) * durationSeconds();
});

$('#audio').addEventListener('timeupdate', () => {
    $('#comment-at').textContent = clock($('#audio').currentTime * 1000);
    drawWaveform();
});
window.addEventListener('resize', drawWaveform);

// ---------- Comments ----------

async function loadComments() {
    const onlyUnresolved = $('#only-unresolved').checked;
    state.comments = await api(
        'GET',
        `/api/projects/${state.project.id}/versions/${state.version.id}/comments?onlyUnresolved=${onlyUnresolved}`,
    );
    $('#comments').replaceChildren(...state.comments.flatMap((comment) => [
        commentItem(comment, false),
        ...comment.replies.map((reply) => commentItem(reply, true)),
    ]));
    drawWaveform();
}

$('#only-unresolved').addEventListener('change', loadComments);

function commentItem(comment, isReply) {
    const item = element('li', '', isReply ? 'reply' : '');
    if (comment.resolved) {
        item.classList.add('resolved');
    }
    if (!isReply) {
        const at = comment.endPositionMs
            ? `${clock(comment.positionMs)}–${clock(comment.endPositionMs)}`
            : clock(comment.positionMs);
        const seek = button(at, () => {
            $('#audio').currentTime = comment.positionMs / 1000;
            $('#audio').play();
        });
        item.append(seek);
    }
    item.append(element('strong', ` ${comment.author.name}: `));
    item.append(comment.removed ? element('em', 'comentário removido', 'muted') : document.createTextNode(comment.body));
    if (comment.edited) {
        item.append(element('span', ' (editado)', 'muted'));
    }
    if (comment.resolution) {
        item.append(element('span', ` · resolvido por ${comment.resolution.by.name}`, 'muted'));
    }
    if (!isReply && !comment.removed) {
        item.append(button('responder', () => reply(comment)));
        item.append(comment.resolved
            ? button('reabrir', () => commentAction(comment, 'reopen'))
            : button('resolver', () => commentAction(comment, 'resolve')));
    }
    return item;
}

$('#comment').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    try {
        await api('POST', `/api/projects/${state.project.id}/versions/${state.version.id}/comments`, {
            positionMs: Math.round($('#audio').currentTime * 1000),
            body: form.body.value,
        });
        form.reset();
        await loadComments();
    } catch (error) {
        alert(error.message);
    }
});

async function reply(comment) {
    const body = prompt('Resposta');
    if (!body) {
        return;
    }
    await api(
        'POST',
        `/api/projects/${state.project.id}/versions/${state.version.id}/comments/${comment.id}/replies`,
        { body },
    );
    await loadComments();
}

async function commentAction(comment, action) {
    try {
        await api(
            'POST',
            `/api/projects/${state.project.id}/versions/${state.version.id}/comments/${comment.id}/${action}`,
        );
        await loadComments();
    } catch (error) {
        alert(error.message);
    }
}

// ---------- Helpers ----------

function element(tag, text, className) {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) {
        node.className = className;
    }
    return node;
}

function badge(text) {
    return element('span', text, 'badge');
}

function button(text, onClick) {
    const node = element('button', text, 'link');
    node.type = 'button';
    node.addEventListener('click', (event) => {
        event.stopPropagation();
        onClick();
    });
    return node;
}

function clock(ms) {
    const seconds = Math.floor(ms / 1000);
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

start().catch((error) => {
    document.body.prepend(element('p', `Erro ao iniciar: ${error.message}`, 'error'));
});
