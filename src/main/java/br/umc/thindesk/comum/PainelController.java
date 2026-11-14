package br.umc.thindesk.comum;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import br.umc.thindesk.seguranca.UsuarioAutenticado;

/**
 * /painel e so um despachante: encaminha cada usuario autenticado para a
 * area do seu proprio perfil.
 */
@Controller
public class PainelController {

	@GetMapping("/painel")
	public String painel(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return switch (usuario.getPerfil()) {
			case ADMINISTRADOR -> "redirect:/admin/dashboard";
			case TECNICO -> "redirect:/tecnico/chamados";
			case SOLICITANTE -> "redirect:/solicitante";
		};
	}

}
