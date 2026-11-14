/*
 * Dashboard administrativo do Thindesk.
 *
 * Consome a API REST (/api/v1) com a sessao do usuario logado (cookie) e o
 * token CSRF lido das meta tags. Cada bloco do painel carrega de forma
 * independente (Promise.allSettled): se um endpoint falhar, so aquele bloco
 * mostra o erro e o resto continua funcionando.
 */
(function () {
    "use strict";

    const raiz = document.getElementById("dashboard");
    const API = raiz.dataset.api.replace(/\/$/, "");
    const FUSO = "America/Sao_Paulo";
    const INTERVALO_AUTO_MS = 60000;
    const TAMANHO_PAGINA = 10;

    const numero = new Intl.NumberFormat("pt-BR");
    const decimal = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    const dataHora = new Intl.DateTimeFormat("pt-BR", { timeZone: FUSO, day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
    const hora = new Intl.DateTimeFormat("pt-BR", { timeZone: FUSO, hour: "2-digit", minute: "2-digit", second: "2-digit" });
    const dataCurta = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC", day: "2-digit", month: "2-digit", year: "numeric" });

    const estado = {
        filtros: {},
        agrupamento: "DIA",
        pagina: 0,
        busca: "",
        timer: null,
        graficos: {},
        carregando: false
    };

    // ------------------------------------------------------------------ utilitarios

    function $(id) {
        return document.getElementById(id);
    }

    function esc(valor) {
        if (valor === null || valor === undefined) {
            return "";
        }
        return String(valor).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    function cssVar(nome) {
        return getComputedStyle(raiz).getPropertyValue(nome).trim();
    }

    function hojeISO() {
        // data de hoje no fuso de Sao Paulo, formato yyyy-mm-dd
        const partes = new Intl.DateTimeFormat("en-CA", { timeZone: FUSO, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
        return partes;
    }

    function somarDias(iso, dias) {
        const d = new Date(iso + "T12:00:00Z");
        d.setUTCDate(d.getUTCDate() + dias);
        return d.toISOString().slice(0, 10);
    }

    function formatarDataISO(iso) {
        return iso ? dataCurta.format(new Date(iso + "T12:00:00Z")) : "—";
    }

    function formatarHoras(horas) {
        if (horas === null || horas === undefined || isNaN(horas)) {
            return "—";
        }
        const totalMin = Math.round(horas * 60);
        const h = Math.floor(totalMin / 60);
        const m = totalMin % 60;
        if (h === 0) {
            return m + " min";
        }
        if (h >= 48) {
            return decimal.format(horas / 24) + " dias";
        }
        return h + "h" + (m ? " " + String(m).padStart(2, "0") + "min" : "");
    }

    function formatarPercentual(valor) {
        return valor === null || valor === undefined ? "—" : decimal.format(valor) + "%";
    }

    function formatarDataHora(instante) {
        return instante ? dataHora.format(new Date(instante)) : "—";
    }

    function debounce(fn, ms) {
        let t;
        return function () {
            const args = arguments;
            clearTimeout(t);
            t = setTimeout(function () { fn.apply(null, args); }, ms);
        };
    }

    // ------------------------------------------------------------------ API

    function csrfHeaders() {
        const token = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        const h = { "Accept": "application/json" };
        if (token && header) {
            h[header.content] = token.content;
        }
        return h;
    }

    function montarQuery(params) {
        const q = new URLSearchParams();
        Object.keys(params).forEach(function (k) {
            const v = params[k];
            if (v !== null && v !== undefined && v !== "") {
                q.append(k, v);
            }
        });
        const s = q.toString();
        return s ? "?" + s : "";
    }

    async function api(caminho, params) {
        let resposta;
        try {
            resposta = await fetch(API + caminho + montarQuery(params || {}), {
                headers: csrfHeaders(),
                credentials: "same-origin"
            });
        } catch (e) {
            throw { status: 0, mensagem: "Não foi possível conectar ao servidor." };
        }
        const tipo = resposta.headers.get("Content-Type") || "";
        const corpo = tipo.includes("json") ? await resposta.json().catch(function () { return null; }) : null;
        if (!resposta.ok) {
            if (resposta.status === 401) {
                throw { status: 401, mensagem: "Sua sessão expirou. Entre novamente para continuar." };
            }
            throw {
                status: resposta.status,
                mensagem: (corpo && (corpo.detail || corpo.title)) || "Erro " + resposta.status + " ao consultar a API.",
                erros: corpo && corpo.erros
            };
        }
        if (corpo === null) {
            throw { status: resposta.status, mensagem: "Resposta inesperada do servidor." };
        }
        return corpo;
    }

    // ------------------------------------------------------------------ estados dos blocos

    function definirEstado(blocoId, tipo, mensagem) {
        const alvo = $(blocoId).querySelector("[data-estado]");
        alvo.className = "dash-estado";
        alvo.innerHTML = "";
        if (tipo === "ok") {
            return;
        }
        if (tipo === "carregando") {
            alvo.classList.add("carregando");
            alvo.insertAdjacentHTML("beforeend", "<span>Carregando...</span>");
        } else if (tipo === "vazio") {
            alvo.textContent = mensagem || "Nenhum chamado encontrado para os filtros selecionados.";
        } else if (tipo === "erro") {
            alvo.classList.add("erro");
            alvo.innerHTML = "<span>" + esc(mensagem) + "</span>";
            const botao = document.createElement("button");
            botao.type = "button";
            botao.className = "btn btn-ghost btn-small";
            botao.textContent = mensagem && mensagem.indexOf("sessão") >= 0 ? "Entrar novamente" : "Tentar de novo";
            botao.addEventListener("click", function () {
                if (botao.textContent === "Entrar novamente") {
                    window.location.href = "/login";
                } else {
                    carregarTudo();
                }
            });
            alvo.appendChild(botao);
        }
    }

    // ------------------------------------------------------------------ filtros (RF05)

    function lerFiltrosDaUrl() {
        const p = new URLSearchParams(window.location.search);
        const fim = p.get("fim") || hojeISO();
        const inicio = p.get("inicio") || somarDias(fim, -29);
        estado.filtros = {
            inicio: inicio,
            fim: fim,
            tipo: p.get("tipo") || "",
            status: p.get("status") || "",
            prioridade: p.get("prioridade") || "",
            tecnicoId: p.get("tecnicoId") || "",
            setor: p.get("setor") || ""
        };
        estado.agrupamento = p.get("agrupamento") || "DIA";
    }

    function escreverFiltrosNaUrl() {
        const params = Object.assign({}, estado.filtros, { agrupamento: estado.agrupamento === "DIA" ? "" : estado.agrupamento });
        history.replaceState(null, "", window.location.pathname + montarQuery(params));
    }

    function preencherFormulario() {
        $("f-inicio").value = estado.filtros.inicio;
        $("f-fim").value = estado.filtros.fim;
        $("f-tipo").value = estado.filtros.tipo;
        $("f-status").value = estado.filtros.status;
        $("f-prioridade").value = estado.filtros.prioridade;
        $("f-tecnico").value = estado.filtros.tecnicoId;
        $("f-setor").value = estado.filtros.setor;
        marcarAtalho();
        document.querySelectorAll(".segmentado button").forEach(function (b) {
            b.classList.toggle("ativo", b.dataset.agrupamento === estado.agrupamento);
        });
    }

    function marcarAtalho() {
        const hoje = hojeISO();
        document.querySelectorAll(".chip[data-dias]").forEach(function (chip) {
            const dias = Number(chip.dataset.dias);
            chip.classList.toggle("ativo", estado.filtros.fim === hoje && estado.filtros.inicio === somarDias(hoje, -(dias - 1)));
        });
    }

    function lerFormulario() {
        return {
            inicio: $("f-inicio").value,
            fim: $("f-fim").value,
            tipo: $("f-tipo").value,
            status: $("f-status").value,
            prioridade: $("f-prioridade").value,
            tecnicoId: $("f-tecnico").value,
            setor: $("f-setor").value
        };
    }

    function validarFiltros(f) {
        if (!f.inicio || !f.fim) {
            return "Informe as datas de início e fim.";
        }
        if (f.inicio > f.fim) {
            return "A data de início não pode ser depois da data de fim.";
        }
        const dias = (new Date(f.fim) - new Date(f.inicio)) / 86400000 + 1;
        if (dias > 366) {
            return "O período máximo é de 366 dias.";
        }
        return null;
    }

    function mostrarErroFiltro(mensagem) {
        const alvo = $("erro-filtro");
        alvo.textContent = mensagem || "";
        alvo.hidden = !mensagem;
    }

    function aplicarFiltros(novos) {
        const erro = validarFiltros(novos);
        if (erro) {
            mostrarErroFiltro(erro);
            return;
        }
        mostrarErroFiltro(null);
        estado.filtros = novos;
        estado.pagina = 0;
        escreverFiltrosNaUrl();
        marcarAtalho();
        carregarTudo();
    }

    function preencherSelect(id, opcoes, rotuloVazio) {
        const select = $(id);
        select.innerHTML = "<option value=\"\">" + esc(rotuloVazio) + "</option>" + opcoes.map(function (o) {
            const codigo = typeof o === "string" ? o : o.codigo;
            const descricao = typeof o === "string" ? o : o.descricao;
            return "<option value=\"" + esc(codigo) + "\">" + esc(descricao) + "</option>";
        }).join("");
    }

    async function carregarOpcoes() {
        try {
            const op = await api("/filtros/opcoes");
            preencherSelect("f-tipo", op.tipos, "Todos");
            preencherSelect("f-status", op.status, "Todos");
            preencherSelect("f-prioridade", op.prioridades, "Todas");
            preencherSelect("f-tecnico", op.tecnicos, "Todos");
            preencherSelect("f-setor", op.setores, "Todos");
        } catch (e) {
            mostrarErroFiltro("Não foi possível carregar as opções de filtro: " + e.mensagem);
        }
    }

    // ------------------------------------------------------------------ RF01 indicadores

    function kpi(rotulo, valor, detalhe, alerta) {
        return "<div class=\"kpi" + (alerta ? " kpi-alerta" : "") + "\">" +
            "<span class=\"kpi-rotulo\">" + esc(rotulo) + "</span>" +
            "<span class=\"kpi-valor\">" + valor + "</span>" +
            (detalhe ? "<span class=\"kpi-detalhe\">" + detalhe + "</span>" : "") +
            "</div>";
    }

    function renderizarIndicadores(d) {
        const emAberto = d.abertos + d.emAtendimento + d.aguardandoSolicitante;
        const atendidos = d.resolvidos + d.fechados;
        let variacao = "Sem dados no período anterior";
        if (d.variacaoTotalPercentual !== null && d.variacaoTotalPercentual !== undefined) {
            const classe = d.variacaoTotalPercentual > 0 ? "variacao-alta" : (d.variacaoTotalPercentual < 0 ? "variacao-baixa" : "");
            variacao = "<span class=\"" + classe + "\">" + decimal.format(Math.abs(d.variacaoTotalPercentual)) + "%</span> vs. período anterior (" + numero.format(d.totalPeriodoAnterior) + ")";
        }
        $("kpis").innerHTML = [
            kpi("Chamados no período", numero.format(d.total), variacao),
            kpi("Em aberto", numero.format(emAberto), numero.format(d.abertos) + " na fila · " + numero.format(d.emAtendimento) + " em atendimento · " + numero.format(d.aguardandoSolicitante) + " aguardando"),
            kpi("Atendidos", numero.format(atendidos), numero.format(d.resolvidos) + " resolvidos · " + numero.format(d.fechados) + " fechados"),
            kpi("Atrasados (fora do SLA)", numero.format(d.atrasados), numero.format(d.cancelados) + " cancelados no período", d.atrasados > 0),
            kpi("Resolvidos no prazo", formatarPercentual(d.percentualDentroSla), "dos chamados atendidos"),
            kpi("Tempo médio do 1º atendimento", formatarHoras(d.tempoMedioPrimeiroAtendimentoHoras), "da abertura até o início"),
            kpi("Tempo médio de resolução", formatarHoras(d.tempoMedioResolucaoHoras), "da abertura até a solução"),
            kpi("Taxa de resolução", formatarPercentual(d.taxaResolucaoPercentual), "atendidos ÷ não cancelados"),
            kpi("Abertos fora do expediente", numero.format(d.abertosForaDoExpediente || 0), "conforme o horário de atendimento de cada setor")
        ].join("");
        $("periodo-descricao").textContent = "Período de " + formatarDataISO(d.inicio) + " a " + formatarDataISO(d.fim) + ".";
        definirEstado("bloco-indicadores", d.total === 0 ? "vazio" : "ok");
        if (d.total === 0) {
            $("kpis").innerHTML = "";
        }
    }

    // ------------------------------------------------------------------ graficos

    function opcoesBase() {
        const texto = cssVar("--cor-texto-secundario");
        const grade = cssVar("--grade");
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: { duration: 300 },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: cssVar("--cor-card-fundo"),
                    titleColor: cssVar("--cor-texto"),
                    bodyColor: cssVar("--cor-texto"),
                    borderColor: cssVar("--cor-borda"),
                    borderWidth: 1,
                    padding: 10,
                    boxPadding: 4,
                    usePointStyle: true
                }
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: texto, maxRotation: 0, autoSkipPadding: 12 }, border: { color: grade } },
                y: { beginAtZero: true, grid: { color: grade }, ticks: { color: texto, precision: 0 }, border: { display: false } }
            }
        };
    }

    // escreve o valor no fim de cada barra horizontal (rotulo direto, sem depender da cor)
    const rotulosNasBarras = {
        id: "rotulosNasBarras",
        afterDatasetsDraw: function (chart) {
            const ctx = chart.ctx;
            ctx.save();
            ctx.font = "12px " + (cssVar("--fonte") || "sans-serif");
            ctx.fillStyle = cssVar("--cor-texto-secundario");
            ctx.textBaseline = "middle";
            chart.getDatasetMeta(0).data.forEach(function (barra, i) {
                const valor = chart.data.datasets[0].data[i];
                ctx.fillText(numero.format(valor), barra.x + 6, barra.y);
            });
            ctx.restore();
        }
    };

    function graficoBarrasHorizontais(id, rotulos, valores, nomeSerie) {
        const base = opcoesBase();
        const config = {
            type: "bar",
            data: {
                labels: rotulos,
                datasets: [{
                    label: nomeSerie,
                    data: valores,
                    backgroundColor: cssVar("--barra"),
                    hoverBackgroundColor: cssVar("--serie-1"),
                    borderRadius: { topRight: 4, bottomRight: 4 },
                    borderSkipped: "left",
                    barPercentage: 0.7,
                    categoryPercentage: 0.85,
                    maxBarThickness: 22
                }]
            },
            options: Object.assign(base, {
                indexAxis: "y",
                layout: { padding: { right: 40 } },
                scales: {
                    x: { beginAtZero: true, grid: { color: cssVar("--grade") }, ticks: { color: cssVar("--cor-texto-secundario"), precision: 0 }, border: { display: false } },
                    y: { grid: { display: false }, ticks: { color: cssVar("--cor-texto") }, border: { color: cssVar("--grade") } }
                }
            }),
            plugins: [rotulosNasBarras]
        };
        if (estado.graficos[id]) {
            estado.graficos[id].data.labels = rotulos;
            estado.graficos[id].data.datasets[0].data = valores;
            estado.graficos[id].update();
        } else {
            estado.graficos[id] = new Chart($(id), config);
        }
    }

    // ------------------------------------------------------------------ RF02 serie temporal

    function renderizarSerie(d) {
        const pontos = d.pontos || [];
        const rotulos = pontos.map(function (p) { return p.rotulo; });
        const abertos = pontos.map(function (p) { return p.abertos; });
        const resolvidos = pontos.map(function (p) { return p.resolvidos; });
        const cor1 = cssVar("--serie-1");
        const cor2 = cssVar("--serie-2");
        const totalAbertos = abertos.reduce(function (a, b) { return a + b; }, 0);
        const totalResolvidos = resolvidos.reduce(function (a, b) { return a + b; }, 0);

        $("legenda-serie").innerHTML =
            "<span><i style=\"background:" + cor1 + "\"></i>Abertos (" + numero.format(totalAbertos) + ")</span>" +
            "<span><i style=\"background:" + cor2 + "\"></i>Resolvidos (" + numero.format(totalResolvidos) + ")</span>";

        const datasets = [
            { label: "Abertos", data: abertos, borderColor: cor1, backgroundColor: cor1, borderWidth: 2, pointRadius: pontos.length > 40 ? 0 : 3, pointHoverRadius: 5, tension: 0.25 },
            { label: "Resolvidos", data: resolvidos, borderColor: cor2, backgroundColor: cor2, borderWidth: 2, pointRadius: pontos.length > 40 ? 0 : 3, pointHoverRadius: 5, tension: 0.25 }
        ];

        if (estado.graficos.serie) {
            estado.graficos.serie.data.labels = rotulos;
            estado.graficos.serie.data.datasets[0].data = abertos;
            estado.graficos.serie.data.datasets[1].data = resolvidos;
            estado.graficos.serie.data.datasets.forEach(function (ds) { ds.pointRadius = pontos.length > 40 ? 0 : 3; });
            estado.graficos.serie.update();
        } else {
            const opcoes = opcoesBase();
            opcoes.interaction = { mode: "index", intersect: false };
            estado.graficos.serie = new Chart($("grafico-serie"), { type: "line", data: { labels: rotulos, datasets: datasets }, options: opcoes });
        }
        definirEstado("bloco-serie", totalAbertos + totalResolvidos === 0 ? "vazio" : "ok");
    }

    // ------------------------------------------------------------------ RF03 situacao

    function renderizarSituacao(d) {
        graficoBarrasHorizontais("grafico-status",
            d.porStatus.map(function (i) { return i.descricao; }),
            d.porStatus.map(function (i) { return i.quantidade; }),
            "Chamados");

        const maior = Math.max.apply(null, d.porPrioridade.map(function (i) { return i.quantidade; }).concat([1]));
        $("lista-prioridade").innerHTML = d.porPrioridade.map(function (i) {
            const largura = Math.round(i.quantidade / maior * 100);
            return "<div class=\"barra-linha\" title=\"" + esc(i.descricao + ": " + i.quantidade + " (" + decimal.format(i.percentual) + "%)") + "\">" +
                "<span>" + esc(i.descricao) + "</span>" +
                "<div class=\"barra-trilho\"><div class=\"barra-preenchida\" style=\"width:" + largura + "%\"></div></div>" +
                "<span class=\"barra-valor\">" + numero.format(i.quantidade) + " · " + decimal.format(i.percentual) + "%</span>" +
                "</div>";
        }).join("");
        definirEstado("bloco-situacao", d.total === 0 ? "vazio" : "ok");
    }

    // ------------------------------------------------------------------ RF04 tipos

    function renderizarTipos(lista) {
        graficoBarrasHorizontais("grafico-tipos",
            lista.map(function (t) { return t.descricao; }),
            lista.map(function (t) { return t.atendidos; }),
            "Atendidos");
        const grafico = estado.graficos["grafico-tipos"];
        grafico.options.plugins.tooltip.callbacks = {
            afterLabel: function (ctx) {
                const t = lista[ctx.dataIndex];
                return t ? [decimal.format(t.percentual) + "% dos atendidos", "Total no período: " + numero.format(t.total), "Tempo médio: " + formatarHoras(t.tempoMedioResolucaoHoras)] : "";
            }
        };
        grafico.update();
        const vazio = lista.length === 0 || lista.every(function (t) { return t.atendidos === 0; });
        definirEstado("bloco-tipos", vazio ? "vazio" : "ok", "Nenhum chamado atendido no período.");
    }

    // ------------------------------------------------------------------ ranking tecnicos

    function renderizarTecnicos(lista) {
        $("tabela-tecnicos").innerHTML = lista.map(function (t) {
            return "<tr>" +
                "<td>" + t.posicao + "º</td>" +
                "<td>" + esc(t.nome) + "</td>" +
                "<td class=\"num\">" + numero.format(t.atendidos) + "</td>" +
                "<td class=\"num\">" + numero.format(t.emAndamento) + "</td>" +
                "<td class=\"num\">" + formatarHoras(t.tempoMedioResolucaoHoras) + "</td>" +
                "<td class=\"num\">" + formatarPercentual(t.percentualDentroSla) + "</td>" +
                "</tr>";
        }).join("");
        definirEstado("bloco-tecnicos", lista.length === 0 ? "vazio" : "ok");
    }

    // ------------------------------------------------------------------ lista de chamados

    function badgeStatus(codigo, descricao) {
        return "<span class=\"badge badge-status-" + esc(codigo.toLowerCase()) + "\">" + esc(descricao) + "</span>";
    }

    function renderizarChamados(pagina) {
        $("tabela-chamados").innerHTML = pagina.conteudo.map(function (c) {
            return "<tr class=\"clicavel\" tabindex=\"0\" data-id=\"" + esc(c.id) + "\">" +
                "<td><strong>" + esc(c.protocolo) + "</strong></td>" +
                "<td>" + esc(c.titulo) + "</td>" +
                "<td>" + esc(c.tipoDescricao) + "</td>" +
                "<td>" + esc(c.prioridadeDescricao) + "</td>" +
                "<td>" + badgeStatus(c.status, c.statusDescricao) + "</td>" +
                "<td>" + esc(c.tecnico || "—") + "</td>" +
                "<td>" + formatarDataHora(c.dataAbertura) + "</td>" +
                "<td>" + (c.status === "CANCELADO" ? "—" : (c.atrasado ? "<span class=\"sla-atrasado\">✕ Atrasado</span>" : "<span class=\"sla-ok\">✓ No prazo</span>")) + "</td>" +
                "</tr>";
        }).join("");
        const inicio = pagina.totalElementos === 0 ? 0 : pagina.pagina * pagina.tamanho + 1;
        const fim = pagina.pagina * pagina.tamanho + pagina.conteudo.length;
        $("info-paginacao").textContent = numero.format(inicio) + "–" + numero.format(fim) + " de " + numero.format(pagina.totalElementos) + " chamados";
        $("pag-anterior").disabled = pagina.pagina === 0;
        $("pag-proxima").disabled = pagina.pagina + 1 >= pagina.totalPaginas;
        definirEstado("bloco-chamados", pagina.totalElementos === 0 ? "vazio" : "ok");
    }

    async function carregarChamados() {
        definirEstado("bloco-chamados", "carregando");
        try {
            const pagina = await api("/chamados", Object.assign({}, estado.filtros, {
                busca: estado.busca, page: estado.pagina, size: TAMANHO_PAGINA, sort: "dataAbertura,desc"
            }));
            renderizarChamados(pagina);
        } catch (e) {
            definirEstado("bloco-chamados", "erro", e.mensagem);
        }
    }

    async function abrirDetalhe(id) {
        const modal = $("modal-chamado");
        $("modal-titulo").textContent = "Carregando...";
        $("modal-conteudo").innerHTML = "<p class=\"dash-sub\">Buscando dados do chamado...</p>";
        if (!modal.open) {
            modal.showModal();
        }
        try {
            const c = await api("/chamados/" + encodeURIComponent(id));
            $("modal-titulo").textContent = c.protocolo + " · " + c.titulo;
            const campo = function (rotulo, valor) { return "<div><span>" + esc(rotulo) + "</span><strong>" + valor + "</strong></div>"; };
            $("modal-conteudo").innerHTML =
                "<div class=\"detalhe-grade\">" +
                campo("Status", badgeStatus(c.status, c.statusDescricao)) +
                campo("Tipo", esc(c.tipoDescricao)) +
                campo("Prioridade", esc(c.prioridadeDescricao) + " (SLA " + c.slaHoras + "h)") +
                campo("Setor", esc(c.setor)) +
                campo("Solicitante", esc(c.solicitante ? c.solicitante.nome : "—")) +
                campo("Técnico", esc(c.tecnico ? c.tecnico.nome : "—")) +
                campo("Abertura", formatarDataHora(c.dataAbertura)) +
                campo("Início do atendimento", formatarDataHora(c.dataInicioAtendimento)) +
                campo("Resolução", formatarDataHora(c.dataResolucao)) +
                campo("Prazo (SLA)", formatarDataHora(c.prazoSla) + (c.status !== "CANCELADO" ? (c.atrasado ? " <span class=\"sla-atrasado\">✕ atrasado</span>" : " <span class=\"sla-ok\">✓ no prazo</span>") : "")) +
                "</div>" +
                "<p>" + esc(c.descricao) + "</p>" +
                "<h4>Considerações técnicas</h4>" +
                (c.consideracoes.length ? "<ul class=\"linha-tempo\">" + c.consideracoes.map(function (x) {
                    return "<li>" + esc(x.texto) + "<small>" + esc(x.autor || "—") + " · " + formatarDataHora(x.data) + "</small></li>";
                }).join("") + "</ul>" : "<p class=\"dash-sub\">Nenhuma consideração registrada.</p>") +
                "<h4>Histórico de status</h4>" +
                "<ul class=\"linha-tempo\">" + c.historico.map(function (h) {
                    return "<li>" + (h.de ? esc(h.de) + " → " : "") + "<strong>" + esc(h.para) + "</strong><small>" + esc(h.usuario || "—") + " · " + formatarDataHora(h.data) + "</small></li>";
                }).join("") + "</ul>";
        } catch (e) {
            $("modal-titulo").textContent = "Não foi possível abrir o chamado";
            $("modal-conteudo").innerHTML = "<p class=\"form-error\">" + esc(e.mensagem) + "</p>";
        }
    }

    // ------------------------------------------------------------------ carga geral (RF06 / RF07)

    async function carregarBloco(blocoId, promessa, renderizar) {
        definirEstado(blocoId, "carregando");
        try {
            renderizar(await promessa);
            return true;
        } catch (e) {
            definirEstado(blocoId, "erro", e.mensagem);
            if (e.status === 400) {
                mostrarErroFiltro(e.mensagem);
            }
            return false;
        }
    }

    async function carregarTudo() {
        if (estado.carregando) {
            return;
        }
        estado.carregando = true;
        $("btn-atualizar").disabled = true;
        const f = estado.filtros;
        const resultados = await Promise.allSettled([
            carregarBloco("bloco-indicadores", api("/dashboard/indicadores", f), renderizarIndicadores),
            carregarBloco("bloco-serie", api("/dashboard/atendimentos-por-periodo", Object.assign({}, f, { agrupamento: estado.agrupamento })), renderizarSerie),
            carregarBloco("bloco-situacao", api("/dashboard/situacao", f), renderizarSituacao),
            carregarBloco("bloco-tipos", api("/dashboard/tipos-mais-atendidos", Object.assign({}, f, { limite: 8 })), renderizarTipos),
            carregarBloco("bloco-tecnicos", api("/dashboard/ranking-tecnicos", Object.assign({}, f, { limite: 5 })), renderizarTecnicos),
            carregarChamados()
        ]);
        const falhas = resultados.filter(function (r) { return r.status === "fulfilled" && r.value === false; }).length;
        $("ultima-atualizacao").textContent = "Atualizado às " + hora.format(new Date()) + (falhas ? " · " + falhas + " bloco(s) com erro" : "");
        $("btn-atualizar").disabled = false;
        estado.carregando = false;
    }

    function configurarAutoAtualizacao(ligado) {
        clearInterval(estado.timer);
        estado.timer = ligado ? setInterval(carregarTudo, INTERVALO_AUTO_MS) : null;
        try {
            localStorage.setItem("thindesk.autoAtualizar", ligado ? "1" : "0");
        } catch (e) {
            // armazenamento indisponivel: a preferencia vale so nesta visita
        }
    }

    // ------------------------------------------------------------------ eventos

    function ligarEventos() {
        $("form-filtros").addEventListener("submit", function (ev) {
            ev.preventDefault();
            aplicarFiltros(lerFormulario());
        });

        $("btn-limpar").addEventListener("click", function () {
            const fim = hojeISO();
            estado.filtros = { inicio: somarDias(fim, -29), fim: fim, tipo: "", status: "", prioridade: "", tecnicoId: "", setor: "" };
            preencherFormulario();
            aplicarFiltros(estado.filtros);
        });

        document.querySelectorAll(".chip[data-dias]").forEach(function (chip) {
            chip.addEventListener("click", function () {
                const fim = hojeISO();
                $("f-fim").value = fim;
                $("f-inicio").value = somarDias(fim, -(Number(chip.dataset.dias) - 1));
                if (Number(chip.dataset.dias) >= 90 && estado.agrupamento === "DIA") {
                    estado.agrupamento = Number(chip.dataset.dias) >= 365 ? "MES" : "SEMANA";
                    preencherFormulario();
                    $("f-fim").value = fim;
                    $("f-inicio").value = somarDias(fim, -(Number(chip.dataset.dias) - 1));
                }
                aplicarFiltros(lerFormulario());
            });
        });

        document.querySelectorAll(".segmentado button").forEach(function (botao) {
            botao.addEventListener("click", function () {
                estado.agrupamento = botao.dataset.agrupamento;
                document.querySelectorAll(".segmentado button").forEach(function (b) { b.classList.toggle("ativo", b === botao); });
                escreverFiltrosNaUrl();
                carregarBloco("bloco-serie", api("/dashboard/atendimentos-por-periodo", Object.assign({}, estado.filtros, { agrupamento: estado.agrupamento })), renderizarSerie);
            });
        });

        $("btn-atualizar").addEventListener("click", carregarTudo);
        $("auto-atualizar").addEventListener("change", function (ev) { configurarAutoAtualizacao(ev.target.checked); });

        $("busca").addEventListener("input", debounce(function (ev) {
            estado.busca = ev.target.value.trim();
            estado.pagina = 0;
            carregarChamados();
        }, 400));

        $("pag-anterior").addEventListener("click", function () {
            if (estado.pagina > 0) {
                estado.pagina--;
                carregarChamados();
            }
        });
        $("pag-proxima").addEventListener("click", function () {
            estado.pagina++;
            carregarChamados();
        });

        $("tabela-chamados").addEventListener("click", function (ev) {
            const linha = ev.target.closest("tr[data-id]");
            if (linha) {
                abrirDetalhe(linha.dataset.id);
            }
        });
        $("tabela-chamados").addEventListener("keydown", function (ev) {
            const linha = ev.target.closest("tr[data-id]");
            if (linha && (ev.key === "Enter" || ev.key === " ")) {
                ev.preventDefault();
                abrirDetalhe(linha.dataset.id);
            }
        });
        $("modal-fechar").addEventListener("click", function () { $("modal-chamado").close(); });
        $("modal-chamado").addEventListener("click", function (ev) {
            if (ev.target === ev.currentTarget) {
                ev.currentTarget.close();
            }
        });
    }

    // ------------------------------------------------------------------ inicio

    async function iniciar() {
        lerFiltrosDaUrl();
        ligarEventos();
        await carregarOpcoes();
        preencherFormulario();
        let auto = false;
        try {
            auto = localStorage.getItem("thindesk.autoAtualizar") === "1";
        } catch (e) {
            auto = false;
        }
        $("auto-atualizar").checked = auto;
        configurarAutoAtualizacao(auto);
        carregarTudo();
    }

    iniciar();
})();
