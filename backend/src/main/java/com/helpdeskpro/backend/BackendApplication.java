package com.helpdeskpro.backend;

import com.helpdeskpro.backend.bootstrap.FirstAdminCommand;
import com.helpdeskpro.backend.bootstrap.FirstAdminProvisioner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        if (args.length == 1 && "provision-admin".equals(args[0])) {
            var console = System.console();
            if (console == null) {
                System.err.println("O provisionamento exige um terminal interativo.");
                System.exit(1);
                return;
            }
            int status;
            try (var context = SpringApplication.run(BackendApplication.class,
                    "--server.port=-1",
                    "--app.seed.demo-enabled=false",
                    "--spring.jpa.hibernate.ddl-auto=validate")) {
                status = FirstAdminCommand.run(console, context.getBean(FirstAdminProvisioner.class));
            }
            System.exit(status);
            return;
        }
        SpringApplication.run(BackendApplication.class, args);
    }

}
