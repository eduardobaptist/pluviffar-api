package br.com.pluviffar.api.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Propriedades próprias da aplicação (prefixo "pluviffar"). */
@ConfigurationProperties(prefix = "pluviffar")
public record PluviffarProperties(
        @DefaultValue Security security,
        @DefaultValue Cors cors
) {
    /**
     * @param secureCookies atributo Secure nos cookies SESSION e XSRF-TOKEN (true em produção; false só em dev/teste via http)
     */
    public record Security(@DefaultValue("true") boolean secureCookies) {
    }

    /**
     * @param allowedOrigins origens do frontend liberadas no CORS (explícitas; nunca "*"). Vazio = CORS desligado.
     */
    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }
}
