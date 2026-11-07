package com.careerradar.common.security;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Small operational helper: {@code ./gradlew :core-api:hashPassword}.
 * Reads the password interactively and prints the BCrypt hash for
 * {@code APP_USER_PASSWORD_HASH}.
 */
public final class PasswordHashTool {

    private PasswordHashTool() {
    }

    public static void main(String[] args) throws IOException {
        String password = args.length > 0 ? args[0] : readPassword();
        if (password == null || password.isBlank()) {
            System.err.println("Usage: ./gradlew :core-api:hashPassword");
            System.exit(1);
        }
        System.out.println(new BCryptPasswordEncoder(10).encode(password));
    }

    private static String readPassword() throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword("Password to hash: ");
            return chars == null ? null : new String(chars);
        }
        System.out.print("Password to hash: ");
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return reader.readLine();
    }
}
