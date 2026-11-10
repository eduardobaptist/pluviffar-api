package br.com.pluviffar.api.auth.security;

import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.entities.User;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Principal guardado na sessão (serializado no Postgres pelo Spring Session — mantenha enxuto e Serializable).
 * getUsername() devolve o ID do usuário (não o email): é a chave de PRINCIPAL_NAME do Spring Session, usada para
 * revogar as sessões de um usuário e estável mesmo se o email mudar.
 */
@Getter
public class AuthenticatedUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final Role role;
    private final boolean active;
    private final boolean mustChangePassword;
    private String passwordHash; // apagado após a autenticação (eraseCredentials)

    private AuthenticatedUser(UUID id, String email, Role role, boolean active, boolean mustChangePassword,
                              String passwordHash) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.passwordHash = passwordHash;
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole(), user.isActive(),
                user.isMustChangePassword(), user.getPasswordHash());
    }

    /** Cópia após a troca de senha (libera o acesso bloqueado por mustChangePassword). */
    public AuthenticatedUser withPasswordChanged() {
        return new AuthenticatedUser(id, email, role, active, false, null);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
