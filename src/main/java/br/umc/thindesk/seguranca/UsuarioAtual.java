package br.umc.thindesk.seguranca;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import br.umc.thindesk.chamado.Pessoa;
import br.umc.thindesk.usuario.Perfil;

/**
 * Usuario logado reduzido ao que a API precisa: referencia (id + nome) e perfil.
 */
public record UsuarioAtual(Pessoa pessoa, Perfil perfil) {

	public static UsuarioAtual de(Authentication authentication) {
		Object principal = authentication.getPrincipal();
		if (principal instanceof UsuarioAutenticado u) {
			return new UsuarioAtual(new Pessoa(u.getId(), u.getNome()), u.getPerfil());
		}
		Perfil perfil = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(a -> a != null && a.startsWith("ROLE_"))
				.map(a -> a.substring(5))
				.filter(UsuarioAtual::perfilValido)
				.map(Perfil::valueOf)
				.findFirst()
				.orElse(Perfil.SOLICITANTE);
		return new UsuarioAtual(new Pessoa(authentication.getName(), authentication.getName()), perfil);
	}

	public boolean isSolicitante() {
		return perfil == Perfil.SOLICITANTE;
	}

	private static boolean perfilValido(String nome) {
		for (Perfil p : Perfil.values()) {
			if (p.name().equals(nome)) {
				return true;
			}
		}
		return false;
	}

}
