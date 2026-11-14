package br.umc.thindesk.chamado;

import java.time.Instant;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import br.umc.thindesk.config.RelogioConfig;

/**
 * Gera protocolos no formato TD-AAAA-000123 com uma sequencia por ano,
 * guardada na colecao "contadores". O findAndModify com $inc e atomico,
 * entao duas aberturas simultaneas nunca recebem o mesmo numero.
 */
@Component
public class GeradorProtocolo {

	static final String COLECAO = "contadores";

	private final MongoTemplate mongoTemplate;

	public GeradorProtocolo(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public String proximo(Instant abertura) {
		int ano = abertura.atZone(RelogioConfig.FUSO).getYear();
		Document contador = mongoTemplate.findAndModify(
				Query.query(Criteria.where("_id").is(chave(ano))),
				new Update().inc("seq", 1),
				FindAndModifyOptions.options().returnNew(true).upsert(true),
				Document.class, COLECAO);
		long seq = contador == null ? 1 : ((Number) contador.get("seq")).longValue();
		return formatar(ano, seq);
	}

	/** Garante que a sequencia do ano seja pelo menos "valor" (usado pela carga inicial). */
	public void ajustarMinimo(int ano, long valor) {
		mongoTemplate.upsert(Query.query(Criteria.where("_id").is(chave(ano))),
				new Update().max("seq", valor), COLECAO);
	}

	public static String formatar(int ano, long seq) {
		return String.format("TD-%d-%06d", ano, seq);
	}

	private static String chave(int ano) {
		return "chamado-" + ano;
	}

}
