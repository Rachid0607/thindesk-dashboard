<p align="center"><img src="src/main/resources/static/images/logo.png" alt="Thindesk Service Manager" width="180"></p>

# Thindesk · Dashboard Administrativo de Chamados

Painel administrativo integrado ao sistema de chamados **Thindesk**, com **API REST própria** e banco MongoDB. O responsável pelos chamados acompanha o andamento, a situação e os prazos (SLA), os tipos de chamado mais atendidos e o desempenho dos técnicos.

Atividade da disciplina de Aplicativos Web · Bacharelado em Sistemas de Informação · UMC · João Pedro Rachid de Abreu · Prof. Alessandro Aparecido

## Entregáveis

| Entregável | Onde está |
|---|---|
| Documento no modelo UMC (capa, folha de rosto, requisitos funcionais e não funcionais, endpoints, evidências de testes, dashboard) | [`docs/Documentacao_Thindesk_Dashboard.pdf`](docs/Documentacao_Thindesk_Dashboard.pdf) · Word: [`docs/Documentacao_Thindesk_Dashboard.docx`](docs/Documentacao_Thindesk_Dashboard.docx) · versão web: [`docs/documentacao.md`](docs/documentacao.md) |
| Vídeo de apresentação (até 3 min) | [`docs/video/apresentacao.mp4`](docs/video/apresentacao.mp4) |
| Evidências de testes | [`docs/evidencias/`](docs/evidencias) (respostas reais da API, prints e relatório dos testes) |
| Requisições prontas para testar a API | [`docs/requisicoes.http`](docs/requisicoes.http) |
| Código-fonte com Gitflow | branches `main`, `develop`, `feature/*` e `release/1.0.0` · tag `v1.0.0` |

## O que tem

- **Dashboard** (`/admin/dashboard`): indicadores gerais (RF01), abertos x resolvidos por dia/semana/mês (RF02), situação por status e prioridade (RF03), tipos mais atendidos e ranking de técnicos (RF04), filtros de período, tipo, status, prioridade, técnico e setor (RF05), integração via API (RF06), atualização manual e automática com tratamento de dados (RF07).
- **API REST `/api/v1`** com 14 endpoints, erros padronizados (RFC 9457) e segurança por perfil.
- **Integração com o Thindesk original**: chamados, clientes e horários de atendimento nas mesmas coleções, identidade visual e o indicador de chamados abertos fora do expediente do setor.
- **Base de segurança** do projeto `login-seguro-spring`: login com BCrypt, bloqueio por tentativas, sessão no MongoDB, perfis ADMINISTRADOR, TÉCNICO e SOLICITANTE, e auditoria.

## Tecnologias

Java 21 · Spring Boot 4.1 · Spring Security 7 · Spring Data MongoDB · Thymeleaf · Chart.js 4 · MongoDB · JUnit 5, Mockito e MockMvc · JaCoCo

## Como rodar

Pré-requisitos: **Java 21** e um **MongoDB** (o Atlas gratuito serve).

1. Copie `.env.example` para `.env` e tire o `#` das linhas que for usar:
   ```properties
   MONGODB_URI=mongodb+srv://usuario:senha@cluster.mongodb.net/
   ADMIN_NOME=Administrador
   ADMIN_EMAIL=admin@thindesk.local
   ADMIN_SENHA=uma-senha-forte-123
   THINDESK_SEED=true
   DEMO_SENHA=senha-dos-tecnicos-123
   ```
2. Rode:
   ```bash
   ./mvnw spring-boot:run        # Linux/Mac
   .\mvnw.cmd spring-boot:run    # Windows
   ```
3. Acesse http://localhost:8080, entre com o administrador e clique em **Dashboard**.

Com `THINDESK_SEED=true`, a primeira subida cria 5 técnicos (ex.: `carlos.andrade@thindesk.local`, senha = `DEMO_SENHA`), 20 clientes, a grade de horários e 420 chamados dos últimos 12 meses.

## Testes

```bash
./mvnw verify
```

São 95 testes. Os de integração usam um MongoDB de verdade e rodam quando `MONGODB_URI` está definida.

## Testar a API rapidamente

```bash
curl -u admin@thindesk.local:SENHA "http://localhost:8080/api/v1/dashboard/indicadores"
```

Todas as chamadas, incluindo os cenários de erro, estão em [`docs/requisicoes.http`](docs/requisicoes.http). A documentação completa de cada endpoint está na [seção 8 da documentação](docs/documentacao.md#8-documentação-da-api).
