package br.umc.thindesk.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.umc.thindesk.chamado.TransicaoStatusInvalidaException;
import br.umc.thindesk.comum.ParametroInvalidoException;
import br.umc.thindesk.comum.RecursoNaoEncontradoException;
import br.umc.thindesk.comum.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Converte as excecoes da API em respostas padronizadas no formato Problem
 * Details (RFC 9457, content-type application/problem+json). Toda resposta de
 * erro tem: type, title, status, detail, instance e timestamp; erros de
 * validacao trazem tambem a lista "erros" (campo + mensagem).
 */
@RestControllerAdvice(basePackages = "br.umc.thindesk.api")
@Order(0)
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ParametroInvalidoException.class)
	public ResponseEntity<ProblemDetail> parametroInvalido(ParametroInvalidoException ex, HttpServletRequest req) {
		ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "parametro-invalido", "Parâmetro inválido", ex.getMessage(), req);
		pd.setProperty("erros", List.of(Map.of("campo", ex.getParametro(), "mensagem", ex.getMessage())));
		return responder(pd);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
		boolean corpo = ex.getParameter().hasParameterAnnotation(RequestBody.class);
		if (corpo) {
			ProblemDetail pd = problema(HttpStatus.UNPROCESSABLE_CONTENT, "validacao", "Dados inválidos",
					"Um ou mais campos do corpo da requisição são inválidos.", req);
			pd.setProperty("erros", erros(ex));
			return responder(pd);
		}
		return parametrosInvalidos(ex, req);
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ProblemDetail> bind(BindException ex, HttpServletRequest req) {
		return parametrosInvalidos(ex, req);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
		String detalhe = "Valor '" + ex.getValue() + "' inválido para o parâmetro '" + ex.getName() + "'.";
		ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "parametro-invalido", "Parâmetro inválido", detalhe, req);
		pd.setProperty("erros", List.of(Map.of("campo", ex.getName(), "mensagem", detalhe)));
		return responder(pd);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ProblemDetail> parametroAusente(MissingServletRequestParameterException ex,
			HttpServletRequest req) {
		return responder(problema(HttpStatus.BAD_REQUEST, "parametro-invalido", "Parâmetro obrigatório ausente",
				"Informe o parâmetro '" + ex.getParameterName() + "'.", req));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ProblemDetail> corpoIlegivel(HttpMessageNotReadableException ex, HttpServletRequest req) {
		return responder(problema(HttpStatus.BAD_REQUEST, "corpo-invalido", "Corpo da requisição inválido",
				"O JSON enviado está malformado ou contém um valor inválido (verifique os campos de enumeração).", req));
	}

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	public ResponseEntity<ProblemDetail> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
		return responder(problema(HttpStatus.NOT_FOUND, "nao-encontrado", "Recurso não encontrado", ex.getMessage(), req));
	}

	@ExceptionHandler(TransicaoStatusInvalidaException.class)
	public ResponseEntity<ProblemDetail> transicaoInvalida(TransicaoStatusInvalidaException ex, HttpServletRequest req) {
		ProblemDetail pd = problema(HttpStatus.CONFLICT, "transicao-invalida", "Transição de status inválida",
				ex.getMessage(), req);
		pd.setProperty("statusAtual", ex.getAtual().name());
		pd.setProperty("destinosPermitidos", ex.getAtual().proximosPermitidos().stream().map(Enum::name).toList());
		return responder(pd);
	}

	@ExceptionHandler(RegraNegocioException.class)
	public ResponseEntity<ProblemDetail> regraNegocio(RegraNegocioException ex, HttpServletRequest req) {
		return responder(problema(HttpStatus.UNPROCESSABLE_CONTENT, "regra-negocio", "Regra de negócio violada",
				ex.getMessage(), req));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ProblemDetail> acessoNegado(AccessDeniedException ex, HttpServletRequest req) {
		return responder(problema(HttpStatus.FORBIDDEN, "acesso-negado", "Acesso negado",
				"Seu perfil não tem permissão para este recurso.", req));
	}

	/**
	 * Demais erros. Excecoes do proprio Spring MVC (405, 415, 404 de rota etc.)
	 * ja carregam status e ProblemDetail; o resto vira 500 sem detalhes internos.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> inesperado(Exception ex, HttpServletRequest req) {
		if (ex instanceof ErrorResponse er) {
			HttpStatusCode status = er.getStatusCode();
			ProblemDetail pd = problema(status, "http-" + status.value(),
					er.getBody().getTitle() == null ? "Erro na requisição" : er.getBody().getTitle(),
					er.getBody().getDetail(), req);
			return responder(pd);
		}
		log.error("Erro inesperado na API em {}", req.getRequestURI(), ex);
		return responder(problema(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro interno",
				"Ocorreu um erro inesperado. Tente novamente em instantes.", req));
	}

	private ResponseEntity<ProblemDetail> parametrosInvalidos(BindException ex, HttpServletRequest req) {
		ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "parametro-invalido", "Parâmetro inválido",
				"Um ou mais parâmetros de consulta são inválidos.", req);
		pd.setProperty("erros", erros(ex));
		return responder(pd);
	}

	private static List<Map<String, Object>> erros(BindException ex) {
		return ex.getFieldErrors().stream().map(ApiExceptionHandler::erro).toList();
	}

	private static Map<String, Object> erro(FieldError fe) {
		String mensagem = fe.isBindingFailure()
				? "Valor '" + fe.getRejectedValue() + "' inválido para '" + fe.getField() + "'."
				: fe.getDefaultMessage();
		return Map.of("campo", fe.getField(), "mensagem", mensagem == null ? "Valor inválido." : mensagem);
	}

	static ProblemDetail problema(HttpStatusCode status, String tipo, String titulo, String detalhe,
			HttpServletRequest req) {
		ProblemDetail pd = ProblemDetail.forStatus(status);
		pd.setType(URI.create("/erros/" + tipo));
		pd.setTitle(titulo);
		pd.setDetail(detalhe);
		pd.setInstance(URI.create(req.getRequestURI()));
		pd.setProperty("timestamp", Instant.now().toString());
		return pd;
	}

	private static ResponseEntity<ProblemDetail> responder(ProblemDetail pd) {
		return ResponseEntity.status(pd.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
	}

}
