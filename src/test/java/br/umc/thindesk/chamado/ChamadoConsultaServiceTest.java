package br.umc.thindesk.chamado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import br.umc.thindesk.comum.ParametroInvalidoException;
import br.umc.thindesk.dashboard.FiltroChamados;

@DisplayName("Listagem de chamados")
class ChamadoConsultaServiceTest {

	private final ChamadoConsultaService service = new ChamadoConsultaService(mock(MongoTemplate.class),
			Clock.systemUTC());
	private final FiltroChamados semFiltro = new FiltroChamados(null, null, null, null, null, null, null);

	@Test
	void ordenacaoPadraoEhPorAberturaDecrescente() {
		Sort sort = ChamadoConsultaService.ordenar(null);
		assertThat(sort.getOrderFor("dataAbertura").getDirection()).isEqualTo(Sort.Direction.DESC);
	}

	@Test
	void ordenacaoAceitaCampoEDirecao() {
		Sort sort = ChamadoConsultaService.ordenar("prazoSla,asc");
		assertThat(sort.getOrderFor("prazoSla").getDirection()).isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void ordenacaoPorCampoNaoPermitidoEhRecusada() {
		assertThatThrownBy(() -> ChamadoConsultaService.ordenar("senhaHash,asc"))
				.isInstanceOf(ParametroInvalidoException.class).extracting("parametro").isEqualTo("sort");
		assertThatThrownBy(() -> ChamadoConsultaService.ordenar("protocolo,para-cima"))
				.isInstanceOf(ParametroInvalidoException.class);
	}

	@Test
	void paginaETamanhoInvalidosSaoRecusados() {
		assertThatThrownBy(() -> service.listar(semFiltro, null, -1, 10, null, null))
				.isInstanceOf(ParametroInvalidoException.class).extracting("parametro").isEqualTo("page");
		assertThatThrownBy(() -> service.listar(semFiltro, null, 0, 101, null, null))
				.isInstanceOf(ParametroInvalidoException.class).extracting("parametro").isEqualTo("size");
	}

	@Test
	void criteriosIncluemRestricaoDoSolicitanteEBuscaEscapada() {
		FiltroChamados filtro = new FiltroChamados(null, null, null, null, null, null, "Financeiro");
		Document doc = ChamadoConsultaService.criterios(filtro, "a.b*", "sol-9").getCriteriaObject();
		String json = doc.toJson();
		assertThat(json).contains("solicitante.usuarioId").contains("sol-9").contains("Financeiro")
				.contains("protocolo").contains("titulo");
		// o texto buscado entra como literal (Pattern.quote), nao como expressao regular
		assertThat(json).contains("\\\\Qa.b*\\\\E");
	}

}
