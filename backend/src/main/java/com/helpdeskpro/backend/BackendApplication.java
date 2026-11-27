package com.helpdeskpro.backend;

import com.helpdeskpro.backend.bootstrap.FirstAdminCommand;
import com.helpdeskpro.backend.bootstrap.FirstAdminProvisioner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        if (args.length == 1 && "recover-admin".equals(args[0])) {
            int status=1;
            try {
                String trust=System.getenv("MAINTENANCE_TRUST_FILE"),backup=System.getenv("MAINTENANCE_BACKUP_FILE");
                if(trust==null||backup==null)throw new IllegalArgumentException("Use o procedimento local autorizado.");
                byte[] input=System.in.readNBytes(24001);
                if(input.length>24000)throw new IllegalArgumentException("Entrada inválida.");
                try(var context=SpringApplication.run(BackendApplication.class,"--server.port=-1",
                        "--app.seed.demo-enabled=false","--spring.jpa.hibernate.ddl-auto=validate","--spring.flyway.enabled=false",
                        "--logging.level.root=OFF","--spring.main.banner-mode=off")) {
                    var request=com.helpdeskpro.backend.bootstrap.MaintenanceAuthorization.verify(new String(input,java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.file.Path.of(trust),java.nio.file.Path.of(backup),context.getBean(org.springframework.jdbc.core.JdbcTemplate.class),java.time.Clock.systemUTC());
                    context.getBean(com.helpdeskpro.backend.bootstrap.LocalAdminRecovery.class).recover(request);
                    System.out.println("Recuperação local concluída e auditada.");status=0;
                } finally {java.util.Arrays.fill(input,(byte)0);}
            } catch(Exception failure) { System.err.println("Manutenção recusada ou não concluída. Verifique autorização, destino, backup e auditoria; nenhum segredo é exibido."); }
            System.exit(status);return;
        }
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
