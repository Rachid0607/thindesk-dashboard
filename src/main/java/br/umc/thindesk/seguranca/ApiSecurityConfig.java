package br.umc.thindesk.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadeia de seguranca so para /api/**, avaliada antes da cadeia das telas.
 *
 * Autenticacao: a mesma sessao do login do sistema (o dashboard chama a API
 * com o cookie de sessao) ou HTTP Basic (para testes com curl/Postman).
 * CSRF continua ativo para chamadas com sessao (o dashboard envia o token no
 * header X-CSRF-TOKEN); requisicoes com header Authorization nao dependem de
 * cookie, entao ficam fora da checagem de CSRF.
 *
 * Autorizacao por perfil:
 * - dashboard e opcoes de filtro: ADMINISTRADOR
 * - mudar status e registrar consideracao: ADMINISTRADOR ou TECNICO
 * - listar, detalhar e abrir chamados: qualquer usuario autenticado
 *   (o SOLICITANTE so enxerga os proprios chamados - regra no controller)
 */
@Configuration
public class ApiSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager)
			throws Exception {
		http
			.securityMatcher("/api/**")
			.authenticationManager(authenticationManager)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/v1/dashboard/**", "/api/v1/filtros/**").hasRole("ADMINISTRADOR")
				.requestMatchers(HttpMethod.PATCH, "/api/v1/chamados/*/status").hasAnyRole("ADMINISTRADOR", "TECNICO")
				.requestMatchers(HttpMethod.POST, "/api/v1/chamados/*/consideracoes").hasAnyRole("ADMINISTRADOR", "TECNICO")
				.requestMatchers("/api/v1/chamados/**", "/api/v1/chamados").authenticated()
				.anyRequest().denyAll())
			.httpBasic(basic -> basic.authenticationEntryPoint(RespostasJsonSeguranca.naoAutenticado()))
			.csrf(csrf -> csrf.ignoringRequestMatchers(request -> request.getHeader("Authorization") != null))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(RespostasJsonSeguranca.naoAutenticado())
				.accessDeniedHandler(RespostasJsonSeguranca.acessoNegado()));
		return http.build();
	}

}
