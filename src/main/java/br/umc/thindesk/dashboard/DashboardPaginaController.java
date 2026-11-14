package br.umc.thindesk.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pagina do dashboard (protegida por /admin/** = ADMINISTRADOR). A pagina so
 * entrega o esqueleto; todos os dados vem da API via JavaScript (dashboard.js).
 */
@Controller
public class DashboardPaginaController {

	@GetMapping("/admin/dashboard")
	public String dashboard() {
		return "dashboard/dashboard";
	}

}
