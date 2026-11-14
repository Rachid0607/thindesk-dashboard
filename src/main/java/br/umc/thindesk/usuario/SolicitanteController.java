package br.umc.thindesk.usuario;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.umc.thindesk.seguranca.UsuarioAutenticado;

@Controller
@RequestMapping("/solicitante")
public class SolicitanteController {

	@GetMapping
	public String meusDados(Model model, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		model.addAttribute("usuario", usuario);
		return "usuario/solicitante-meus-dados";
	}

}
