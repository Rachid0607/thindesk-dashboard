package br.umc.thindesk.chamado;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import br.umc.thindesk.auditoria.AuditoriaService;
import br.umc.thindesk.auditoria.TipoEvento;
import br.umc.thindesk.comum.RecursoNaoEncontradoException;
import br.umc.thindesk.comum.RegraNegocioException;
import br.umc.thindesk.usuario.Perfil;
import br.umc.thindesk.usuario.Usuario;
import br.umc.thindesk.usuario.UsuarioRepository;

/**
 * Regras de negocio do ciclo de vida do chamado: abertura, mudanca de status
 * (pela maquina de estados de StatusChamado) e consideracoes tecnicas.
 */
@Service
public class ChamadoService {

	private final ChamadoRepository chamadoRepository;
	private final UsuarioRepository usuarioRepository;
	private final GeradorProtocolo geradorProtocolo;
	private final AuditoriaService auditoriaService;
	private final Clock clock;

	public ChamadoService(ChamadoRepository chamadoRepository, UsuarioRepository usuarioRepository,
			GeradorProtocolo geradorProtocolo, AuditoriaService auditoriaService, Clock clock) {
		this.chamadoRepository = chamadoRepository;
		this.usuarioRepository = usuarioRepository;
		this.geradorProtocolo = geradorProtocolo;
		this.auditoriaService = auditoriaService;
		this.clock = clock;
	}

	public Chamado buscar(String id) {
		return chamadoRepository.findById(id)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Chamado " + id + " não encontrado."));
	}

	public Chamado abrir(String titulo, String descricao, TipoChamado tipo, Prioridade prioridade, String setor,
			Pessoa solicitante) {
		Instant agora = clock.instant();
		Chamado chamado = new Chamado();
		chamado.setTitulo(titulo.trim());
		chamado.setDescricao(descricao.trim());
		chamado.setTipo(tipo);
		chamado.setPrioridade(prioridade);
		chamado.setSetor(setor.trim());
		chamado.setSolicitante(solicitante);
		chamado.setStatus(StatusChamado.ABERTO);
		chamado.setDataAbertura(agora);
		chamado.setPrazoSla(RegraSla.calcularPrazo(prioridade, agora));
		chamado.setProtocolo(geradorProtocolo.proximo(agora));
		chamado.getHistorico().add(new MudancaStatus(null, StatusChamado.ABERTO, solicitante, agora));
		Chamado salvo = chamadoRepository.save(chamado);
		auditoriaService.registrar(TipoEvento.CHAMADO_ABERTO, solicitante.usuarioId(),
				"Chamado " + salvo.getProtocolo() + " aberto");
		return salvo;
	}

	/**
	 * Muda o status respeitando a maquina de estados. Ao entrar em atendimento
	 * pela primeira vez, o chamado precisa de um tecnico: o informado em
	 * tecnicoId ou, se nenhum for informado, o proprio usuario quando ele for
	 * TECNICO.
	 */
	public Chamado alterarStatus(String id, StatusChamado destino, String tecnicoId, String comentario,
			Pessoa usuario, Perfil perfilUsuario) {
		Chamado chamado = buscar(id);
		StatusChamado atual = chamado.getStatus();
		if (!atual.podeIrPara(destino)) {
			throw new TransicaoStatusInvalidaException(atual, destino);
		}

		Instant agora = clock.instant();
		if (tecnicoId != null && !tecnicoId.isBlank()) {
			chamado.setTecnico(buscarTecnico(tecnicoId));
		}
		if (destino == StatusChamado.EM_ATENDIMENTO && chamado.getTecnico() == null) {
			if (perfilUsuario != Perfil.TECNICO) {
				throw new RegraNegocioException("Informe o técnico responsável (tecnicoId) para iniciar o atendimento.");
			}
			chamado.setTecnico(usuario);
		}

		switch (destino) {
			case EM_ATENDIMENTO -> {
				if (chamado.getDataInicioAtendimento() == null) {
					chamado.setDataInicioAtendimento(agora);
				}
				chamado.setDataResolucao(null);
			}
			case RESOLVIDO -> chamado.setDataResolucao(agora);
			case FECHADO, CANCELADO -> chamado.setDataFechamento(agora);
			default -> {
				// AGUARDANDO_SOLICITANTE nao altera datas
			}
		}

		chamado.setStatus(destino);
		chamado.getHistorico().add(new MudancaStatus(atual, destino, usuario, agora));
		if (comentario != null && !comentario.isBlank()) {
			chamado.getConsideracoes().add(new ConsideracaoTecnica(comentario.trim(), usuario, agora));
		}
		Chamado salvo = chamadoRepository.save(chamado);
		auditoriaService.registrar(TipoEvento.CHAMADO_STATUS_ALTERADO, usuario.usuarioId(),
				"Chamado " + salvo.getProtocolo() + ": " + atual + " -> " + destino);
		return salvo;
	}

	public Chamado adicionarConsideracao(String id, String texto, Pessoa autor) {
		Chamado chamado = buscar(id);
		if (chamado.getStatus().isFinal()) {
			throw new RegraNegocioException("Chamados " + chamado.getStatus() + " não aceitam novas considerações.");
		}
		chamado.getConsideracoes().add(new ConsideracaoTecnica(texto.trim(), autor, clock.instant()));
		return chamadoRepository.save(chamado);
	}

	private Pessoa buscarTecnico(String tecnicoId) {
		Usuario tecnico = usuarioRepository.findById(tecnicoId)
				.filter(u -> u.getPerfil() == Perfil.TECNICO && u.isAtivo())
				.orElseThrow(() -> new RegraNegocioException("Técnico " + tecnicoId + " não encontrado ou inativo."));
		return new Pessoa(tecnico.getId(), tecnico.getNome());
	}

}
