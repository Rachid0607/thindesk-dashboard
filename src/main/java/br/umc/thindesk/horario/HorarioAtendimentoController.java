package br.umc.thindesk.horario;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.umc.thindesk.comum.RegraNegocioException;
import jakarta.validation.Valid;

/**
 * Configuracao de horario de atendimento por setor (somente ADMINISTRADOR),
 * equivalente a tela "ajustes-horarios" do Thindesk original.
 */
@Controller
@RequestMapping("/configuracoes/horarios")
public class HorarioAtendimentoController {

	static final List<DayOfWeek> DIAS = List.of(DayOfWeek.values());

	private final HorarioAtendimentoService service;

	public HorarioAtendimentoController(HorarioAtendimentoService service) {
		this.service = service;
	}

	@GetMapping
	public String listar(Model model) {
		preparar(model);
		if (!model.containsAttribute("horarioForm")) {
			model.addAttribute("horarioForm", new HorarioForm());
		}
		return "horario/horarios";
	}

	@PostMapping
	public String salvar(@Valid @ModelAttribute("horarioForm") HorarioForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes) {
		if (!bindingResult.hasErrors()) {
			try {
				service.salvar(form);
				redirectAttributes.addFlashAttribute("mensagemSucesso", "Horário salvo.");
				return "redirect:/configuracoes/horarios";
			} catch (RegraNegocioException e) {
				bindingResult.rejectValue("horarioFim", "horario.invalido", e.getMessage());
			}
		}
		preparar(model);
		return "horario/horarios";
	}

	@PostMapping("/{id}/excluir")
	public String excluir(@PathVariable String id, RedirectAttributes redirectAttributes) {
		service.excluir(id);
		redirectAttributes.addFlashAttribute("mensagemSucesso", "Horário removido.");
		return "redirect:/configuracoes/horarios";
	}

	private void preparar(Model model) {
		model.addAttribute("horarios", service.listar(null));
		model.addAttribute("dias", DIAS);
	}

}
