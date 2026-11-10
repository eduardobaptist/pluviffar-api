package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.Map;

/**
 * Seed do primeiro ADMIN.
 * Email e senha vêm de variáveis de ambiente (PLUVIFFAR_ADMIN_EMAIL / PLUVIFFAR_ADMIN_PASSWORD),
 * repassadas como placeholders do Flyway (spring.flyway.placeholders.*). O hash BCrypt é gerado aqui,
 * então nenhuma senha ou hash fica no repositório. Falha se as variáveis estiverem vazias.
 */
public class V2__SeedAdmin extends BaseJavaMigration {

    private static final int MIN_PASSWORD_LENGTH = 12;

    @Override
    public void migrate(Context context) throws Exception {
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        String email = placeholders.getOrDefault("admin_email", "").trim().toLowerCase(Locale.ROOT);
        String password = placeholders.getOrDefault("admin_password", "");

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Seed do ADMIN: defina PLUVIFFAR_ADMIN_EMAIL e PLUVIFFAR_ADMIN_PASSWORD (ver .env.example)");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "Seed do ADMIN: PLUVIFFAR_ADMIN_PASSWORD deve ter ao menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }

        String sql = """
                INSERT INTO app.users (name, email, password_hash, role, must_change_password)
                VALUES (?, ?, ?, 'ADMIN', true)
                """;
        try (PreparedStatement ps = context.getConnection().prepareStatement(sql)) {
            ps.setString(1, "Administrador");
            ps.setString(2, email);
            ps.setString(3, new BCryptPasswordEncoder().encode(password));
            ps.executeUpdate();
        }
    }
}
