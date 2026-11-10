package br.com.pluviffar.api.user.entities;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Perfil do usuário. ADMIN: superusuário (gerencia usuários e vê tudo). "
        + "PRODUCER: gerencia apenas os próprios gateways e estações.")
public enum Role {
    ADMIN,
    PRODUCER;

    /** Authority usada pelo Spring Security (hasRole('ADMIN') ⇔ ROLE_ADMIN). */
    public String authority() {
        return "ROLE_" + name();
    }
}
