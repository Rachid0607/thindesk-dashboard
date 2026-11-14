package br.umc.thindesk.cliente;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * Tela de clientes (ADMINISTRADOR e TECNICO), como no Thindesk original.
 */
@Controller
@RequestMapping("/clientes")
public class ClienteController {

	private final ClienteService clienteService;

	public ClienteController(ClienteService clienteService) {
		this.clienteService = clienteService;
	}

	@GetMapping
	public String listar(Model model) {
		model.addAttribute("clientes", clienteService.listar(null));
		if (!model.containsAttribute("clienteForm")) {
			model.addAttribute("clienteForm", new ClienteForm());
		}
		return "cliente/clientes";
	}

	@PostMapping
	public String cadastrar(@Valid @ModelAttribute("clienteForm") ClienteForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("clientes", clienteService.listar(null));
			return "cliente/clientes";
		}
		clienteService.cadastrar(form);
		redirectAttributes.addFlashAttribute("mensagemSucesso", "Cliente cadastrado com sucesso.");
		return "redirect:/clientes";
	}

}
