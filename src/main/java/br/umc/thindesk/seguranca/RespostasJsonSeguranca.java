package br.umc.thindesk.seguranca;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Na API, falta de autenticacao (401) e de permissao (403) respondem em JSON
 * (Problem Details) em vez de redirecionar para a tela de login. O 401 nao
 * envia WWW-Authenticate, para o navegador nao abrir a janela de senha do
 * HTTP Basic quando a sessao do dashboard expira.
 */
public final class RespostasJsonSeguranca {

	private RespostasJsonSeguranca() {
	}

	public static AuthenticationEntryPoint naoAutenticado() {
		return (request, response, ex) -> escrever(request, response, 401, "nao-autenticado", "Não autenticado",
				"Faça login (sessão do sistema ou HTTP Basic) para acessar a API.");
	}

	public static AccessDeniedHandler acessoNegado() {
		return (request, response, ex) -> escrever(request, response, 403, "acesso-negado", "Acesso negado",
				"Seu perfil não tem permissão para este recurso.");
	}

	private static void escrever(HttpServletRequest request, HttpServletResponse response, int status, String tipo,
			String titulo, String detalhe) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write("{\"type\":\"/erros/" + tipo + "\",\"title\":\"" + titulo + "\",\"status\":" + status
				+ ",\"detail\":\"" + detalhe + "\",\"instance\":\"" + json(request.getRequestURI())
				+ "\",\"timestamp\":\"" + Instant.now() + "\"}");
	}

	private static String json(String valor) {
		return valor.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
