package br.umc.thindesk.cliente;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cliente atendido pelo suporte (vem do Thindesk original: nome, telefone e
 * setor, na mesma colecao "clientes"). O setor do cliente e usado como setor
 * dos chamados que ele abre.
 */
@Document(collection = "clientes")
public class Cliente {

	@Id
	private String id;

	@Indexed
	private String nome;

	private String telefone;

	@Indexed
	private String setor;

	private String email;

	private Instant criadoEm;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getTelefone() {
		return telefone;
	}

	public void setTelefone(String telefone) {
		this.telefone = telefone;
	}

	public String getSetor() {
		return setor;
	}

	public void setSetor(String setor) {
		this.setor = setor;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public void setCriadoEm(Instant criadoEm) {
		this.criadoEm = criadoEm;
	}

}
