package br.umc.thindesk.api.dto;

import br.umc.thindesk.cliente.Cliente;

public record ClienteDTO(String id, String nome, String telefone, String setor, String email) {

	public static ClienteDTO de(Cliente c) {
		return new ClienteDTO(c.getId(), c.getNome(), c.getTelefone(), c.getSetor(), c.getEmail());
	}

}
