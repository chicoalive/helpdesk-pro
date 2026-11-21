# Consolidação das Etapas 14.1A, 14.1B e 14.1C

Revisão de 21/11/2026. Identidade pública: Meu Assistente TI. Nome interno do repositório e pacotes preservados.

## Escopo concluído

- **14.1A:** instalação sem administrador automático; provisionamento local interativo, senha oculta, recusa quando já existe ADMIN e serialização de execuções concorrentes. Demonstração requer ativação explícita e ambiente separado.
- **14.1B:** usuários ativos/inativos, JWT por ID e versão de sessão, cadastro dos três perfis, edição, desativação/reativação, último ADMIN protegido na aplicação e no PostgreSQL, auditoria transacional, conflitos de versão e preservação de vínculos históricos.
- **14.1C:** solicitação pública genérica e limitada, aprovação/rejeição administrativa, autorização temporária aleatória de uso único persistida apenas por hash, redefinição BCrypt e revogação das sessões anteriores. Não há envio de e-mail: entrega pelo ADMIN por canal confiável após verificar identidade.
- **Identidade visual:** fotografia capa4, logo aprovada e painel de vidro preservados; telas de usuários e recuperação mantêm a linguagem visual.
- **Regressões homologadas:** atribuição concorrente de técnico, reatribuição quando o responsável fica inativo/perde perfil, comentários com UTC explícito e tratamento de erro assíncrono no login.

## Evidências da consolidação

| Verificação | Resultado |
| --- | --- |
| Backend completo, PostgreSQL real descartável + H2 | 238 aprovados; 0 falhas, 0 erros, 0 pulados |
| Frontend completo, Node 24 em contêiner Linux isolado | 214 aprovados em 23 arquivos; 0 falhas |
| Produção backend | Gradle bootJar concluído |
| Produção frontend | Angular concluído; total inicial 451,24 kB |
| Migrações | V1–V6 conferidas com o histórico principal; checksums das migrações aplicadas correspondem aos arquivos locais |
| Migração sobre registros existentes e instalação nova | Testes PostgreSQL aprovados; V1 pode constar como baseline em instalação legada, sem checksum de script |
| Recuperação, sessões e gestão | Testes de perfis, conta inativa, expiração, revogação, reuso, concorrência e rollback aprovados |
| Integridade principal | Contagens e comparação dos registros históricos preservadas; nenhum cadastro/reset de teste no principal |
| Administradores | Conta antiga inativa, login 401; conta principal ativa com login e operações administrativas 200 |
| Credencial protegida | Leitura DPAPI e autenticação conferidas em memória, sem publicar senha ou arquivo |
| Identidade em execução | Fotografia e logo servidas correspondem aos arquivos locais aprovados |
| Rede | Frontend 127.0.0.1:4200, backend 127.0.0.1:8080; PostgreSQL principal sem porta publicada no ambiente em execução |
| Backup | Arquivo protegido fora do Git, checksum inalterado; restauração isolada homologada antes da atualização |

O banco principal mantém 5 usuários (4 ativos), 2 registros ADMIN (1 ativo), 7 chamados, 4 ativos, 21 eventos históricos de chamados, 0 comentários, 1 anexo e 1 evento administrativo legítimo de desativação. A desativação ocorreu na 14.1C; esta consolidação não alterou contas, senhas ou dados.

As dependências locais do frontend foram instaladas para Linux. Executar diretamente no Windows produz erro de plataforma do esbuild; a validação foi concluída em contêiner Node 24, sem reinstalar dependências nem modificar os serviços principais.

## Segurança do versionamento

- Busca por segredos reais configurados, senhas protegidas, JWTs, chaves privadas, arquivos de credenciais/DPAPI, dumps e backups nos candidatos ao Git. Nenhuma credencial real identificada no conjunto publicado.
- Credenciais fictícias permanecem somente onde necessárias a testes e demonstração explícita; não são credenciais de produção. Nunca habilitar demonstração em banco de uso real.
- Removido o fallback JWT conhecido do desenvolvimento. JWT_SECRET passa a ser obrigatório fora dos testes, inclusive no desenvolvimento local; o perfil de produção já o exigia. O segredo real configurado no ambiente foi verificado como distinto desse fallback.
- `.gitignore` ampliado para credenciais, chaves, backups e artefatos operacionais. Fotografias descartadas de propostas permanecem locais e ignoradas, sem apagar arquivos. Apenas a fotografia aprovada e os arquivos de marca apropriados integram esta consolidação.
- Compose passa a publicar por padrão apenas em loopback. Esta revisão não reiniciou ou reconfigurou os contêineres principais.
- Documentação pública usa instruções genéricas de instalação; caminhos privados e conta principal real ficam no inventário local.
- CI passa a ter PostgreSQL efêmero, permitindo executar as integrações antes opcionais, além do artefato backend e dos testes/build frontend.

Não foi encontrado indício de comprometimento dos segredos reais examinados; não se realizou rotação nesta execução. Segredos devem ser rotacionados se houver evidência externa de exposição ou uso anterior de valor público.

## Pendências para uso comercial

1. **Continuidade administrativa:** existe um único ADMIN ativo. A perda simultânea de senha, sessão e meios externos de recuperação ainda não tem mecanismo de manutenção local implementado. Procedimento e limite explícitos em [Continuidade administrativa](CONTINUIDADE-ADMINISTRATIVA.md). É uma pendência importante antes do uso comercial.
2. **Entrega de autorizações:** operacional e manual por canal confiável; não há serviço de e-mail. Exige verificação de identidade pelo responsável.
3. **Rate limiting atrás do proxy:** origem pode ser compartilhada entre usuários; avaliar capacidade e política antes de acesso por empresa/rede. Não confiar em cabeçalhos encaminhados sem uma configuração explícita de proxy confiável.
4. **CSS:** avisos existentes de orçamento em ticket-detail.css (4,23 kB) e user-management.css (4,31 kB); build passa sem ampliar limites.
5. **Rotinas e negócio:** aprovar o levantamento [14.2](PLANO-14.2.md), em especial política de SLA, atribuições e contingência operacional. Não implementado nesta consolidação.

Etapa 15 permanece suspensa. Nenhuma conta adicional foi criada e nenhuma credencial real foi alterada.
