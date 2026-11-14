package br.umc.thindesk.auth;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.umc.thindesk.auditoria.AuditoriaService;
import br.umc.thindesk.auditoria.TipoEvento;
import br.umc.thindesk.usuario.Perfil;
import br.umc.thindesk.usuario.Usuario;
import br.umc.thindesk.usuario.UsuarioService;

@Service
public class CadastroService {

	private final UsuarioService usuarioService;
	private final PasswordEncoder passwordEncoder;
	private final AuditoriaService auditoriaService;

	public CadastroService(UsuarioService usuarioService, PasswordEncoder passwordEncoder,
			AuditoriaService auditoriaService) {
		this.usuarioService = usuarioService;
		this.passwordEncoder = passwordEncoder;
		this.auditoriaService = auditoriaService;
	}

	public boolean emailJaCadastrado(String email) {
		return usuarioService.existePorEmail(email);
	}

	/**
	 * Cadastro publico: perfil e sempre SOLICITANTE, nunca o que vier do formulario.
	 */
	public Usuario cadastrar(CadastroForm form) {
		Usuario usuario = new Usuario();
		usuario.setNome(form.getNome().trim());
		usuario.setEmail(form.getEmail());
		usuario.setSenhaHash(passwordEncoder.encode(form.getSenha()));
		usuario.setPerfil(Perfil.SOLICITANTE);
		usuario.setAtivo(true);
		usuario.setConsentimentoTermosEm(Instant.now());
		Usuario salvo = usuarioService.salvar(usuario);
		auditoriaService.registrar(TipoEvento.USUARIO_REGISTRADO, salvo.getId(), "Cadastro publico (SOLICITANTE)");
		return salvo;
	}

}
