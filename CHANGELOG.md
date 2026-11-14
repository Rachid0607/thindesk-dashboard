# Changelog

## [1.0.0] - 2026-11-14

### Adicionado
- Dashboard administrativo (`/admin/dashboard`) com indicadores gerais, gráfico de abertos x resolvidos, situação por status e prioridade, tipos mais atendidos, ranking de técnicos, lista de chamados com detalhe, filtros e atualização automática.
- API REST `/api/v1` com 14 endpoints (dashboard, chamados, clientes e horários de atendimento) e erros no formato Problem Details (RFC 9457).
- Modelo de chamado com tipo, prioridade e status controlados, datas do ciclo de vida, SLA, considerações técnicas e histórico.
- Integração com o Thindesk original: clientes, horários de atendimento por setor, identidade visual (logo, cor e menu lateral) e indicador de chamados abertos fora do expediente.
- Carga de dados de demonstração (5 técnicos, 20 clientes, grade de horários e 420 chamados em 12 meses).
- 95 testes automatizados (unitários, de controller e de integração com MongoDB) e relatório de cobertura (JaCoCo).
- Documentação no modelo da UMC (Word e PDF), evidências de testes e vídeo de apresentação.

### Base
- Autenticação, perfis, sessão no MongoDB, auditoria e temas vindos do projeto `login-seguro-spring`.
