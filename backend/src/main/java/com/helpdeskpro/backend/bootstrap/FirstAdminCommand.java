package com.helpdeskpro.backend.bootstrap;

import java.io.Console;
import java.util.Arrays;

public final class FirstAdminCommand {
    private FirstAdminCommand() {}

    public static int run(Console console, FirstAdminProvisioner provisioner) {
        String name = console.readLine("Nome: ");
        String email = console.readLine("E-mail: ");
        char[] password = console.readPassword("Senha (mín. 12 caracteres, máx. 72 bytes UTF-8): ");
        char[] confirmation = null;
        try {
            if (name == null || email == null || password == null) {
                return 1;
            }
            confirmation = console.readPassword("Confirme a senha: ");
            if (!Arrays.equals(password, confirmation)) {
                console.printf("As senhas não coincidem.%n");
                return 1;
            }
            try {
                if (!provisioner.create(name, email, password)) {
                    console.printf("Já existe um administrador ou o e-mail informado já está em uso.%n");
                    return 1;
                }
            } catch (IllegalArgumentException exception) {
                console.printf("%s%n", exception.getMessage());
                return 1;
            }
            console.printf("Primeiro administrador criado.%n");
            return 0;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}