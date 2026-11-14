package br.umc.thindesk.chamado;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela "Chamados" do menu (ADMINISTRADOR e TECNICO): ultimos 50 chamados.
 */
@Controller
public class ChamadoPaginaController {

	private final ChamadoRepository chamadoRepository;

	public ChamadoPaginaController(ChamadoRepository chamadoRepository) {
		this.chamadoRepository = chamadoRepository;
	}

	@GetMapping("/chamados")
	public String listar(Model model) {
		model.addAttribute("chamados", chamadoRepository
				.findAll(org.springframework.data.domain.PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "dataAbertura")))
				.getContent());
		model.addAttribute("titulo", "Chamados");
		model.addAttribute("subtitulo", "Últimos 50 chamados abertos. Para filtros e indicadores, use o dashboard.");
		return "chamado/lista-chamados";
	}

}
