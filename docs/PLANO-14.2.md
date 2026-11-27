# Levantamento da Etapa 14.2

**Atualização em 26/11/2026:** 14.2.1–14.2.5 implementadas, homologadas e aplicadas localmente. Estado atual: 516 testes backend, 250 frontend e Flyway V1–V11. Rotinas Windows e contingência administrativa no GUIA-OPERACIONAL.md. Etapa 15 e chatbot suspensos. O texto abaixo preserva o levantamento e as atualizações históricas.

Somente levantamento técnico. Nenhuma funcionalidade desta etapa foi implementada na consolidação de 14.1A–C. Etapa 15 suspensa.

## Estado observado e entregas propostas

| Área | Já existe | Lacuna ou decisão necessária | Testes da próxima entrega |
| --- | --- | --- | --- |
| Chamados | Abertura, prevenção de duplicados, estados validados, solução, cancelamento, comentários, imagem e auditoria; solicitante vê seus registros | Homologar cada transição com os três perfis; definir reabertura e comentários em estados finais antes de mudar regras | Matriz estado × perfil × vínculo, solução vazia, duplicidade concorrente, leitura/anexo de outro solicitante |
| Atribuições | Bloqueio do chamado e técnico de destino, técnico ativo, conflito de atribuição; ADMIN substitui técnico inativo ou que perdeu perfil | Decidir política de transferência de técnico ativo e papel do técnico em atribuir outro técnico; nenhuma mudança automática de responsável | Corridas atribuição/desativação/alteração de perfil, transição de estado simultânea, ausência de sobrescrita silenciosa |
| SLA | Prazo calculado por prioridade: 4/24/72 horas corridas; estados terminais deixam de aparecer como atrasados | Não há calendário útil, pausa em AGUARDANDO_USUARIO, política configurável, escalonamento ou histórico de cumprimento final | Limite exato do prazo, UTC/fuso, encerramento atrasado, mudança de prioridade, aprovação explícita da política de pausa |
| Ativos | Cadastro, edição, estados, unicidade de código, vínculo em incidentes de equipamento e seleção de itens em uso | Validar normalização do código, concorrência de unicidade, descarte/manutenção com chamados abertos; exclusão física e ausência de auditoria própria requerem avaliação | Mesmo código simultâneo, atualização de ativo vinculado, tentativa de excluir ativo com histórico, permissões diretas |
| Permissões | RBAC no backend, propriedade de chamados do solicitante, sessões versionadas, contas inativas bloqueadas, último ADMIN protegido | Confirmar regras operacionais de atribuição e comentários; revisar anexos por conteúdo real, não apenas MIME informado | Manipulação direta de IDs, perfis e métodos HTTP; arquivo falso, nome malicioso, limite de tamanho e acesso indevido |
| Rotinas | Dashboard, filtros atuais, impressão, auditoria de chamados e contas, backups locais validados | Definir triagem, prioridades, cobertura de atendimento, recuperação de contingência, retenção e ensaio periódico de restauração | Jornada de expediente em cópia isolada, desempenho com volume representativo, plano de restauração e retomada |

## Ordem recomendada

1. **14.2.1 — Homologação operacional de chamados e permissões.** Fechar matriz de estados/perfis e testar corridas em mudanças de status, comentários, anexos e duplicidade. Não presumir que os testes de contas cobrem essas corridas.
2. **14.2.2 — Política de atribuição e continuidade do atendimento.** Aprovar transferência e triagem, validar responsáveis inativos e histórico, depois implementar somente regras aprovadas.
3. **14.2.3 — Política e medição de SLA.** Primeiro definir horas corridas/úteis, pausa, horário e feriados; medir cumprimento histórico e atrasos de acordo com a política aprovada.
4. **14.2.4 — Ciclo de vida de ativos e anexos.** Preservar vínculos, revisar exclusão, normalização, auditoria e validação real de arquivos.
5. **14.2.5 — Rotinas e homologação de empresa.** Ensaiar jornada integrada, backup/restauração, contingência administrativa e operação sem intervenção do desenvolvedor.

Cada entrega deve incluir critério de aceite, testes PostgreSQL isolados, regressão frontend/backend, inspeção visual quando aplicável e comparação do banco principal antes/depois. Sem usuários fictícios no principal e sem publicação externa.

## Atualização de 24/11/2026 — 14.2.2

Atribuição e transferência implementadas conforme [homologação 14.2.2](HOMOLOGACAO-14.2.2.md): 425 testes backend e 229 frontend aprovados. V8 acrescenta justificativa à auditoria, preservando V1–V7 e o histórico. Transferência administrativa somente em estados ativos; técnico somente assume chamado sem responsável. Próxima entrega: 14.2.3, com definição prévia da política de SLA. Etapa 15 suspensa.

## Atualização de 24/11/2026 — 14.2.3

[14.2.3 homologada e aplicada](HOMOLOGACAO-14.2.3.md): 454 testes backend e 244 frontend aprovados. V9 registra novas resoluções e o prazo vigente, sem preencher histórico antigo. Mantidas 4/24/72 horas corridas, sem pausa ou calendário comercial. SLA atual, resultado histórico, cancelamento e ausência de informação são apresentados separadamente. 14.2.4/14.2.5 permanecem pendentes; Etapa 15 suspensa.

## Atualização de 26/11/2026 — 14.2.4 e continuidade

[Ativos e anexos homologados](HOMOLOGACAO-14.2.4.md): versão e auditoria patrimonial transacional, unicidade normalizada, preservação do histórico e validação real de PNG/JPEG até 2 MiB. V10 validada em restauração isolada antes da aplicação principal. Suítes: 502 backend e 250 frontend; builds aprovados e navegação desktop/celular com os três perfis. Na retomada, serviços locais e dados reconferidos; backup atual restaurado com 14 tabelas idênticas. Login e identidade visual preservados. Próxima entrega: 14.2.5, sem implementação nesta execução. Etapa 15 e chatbot suspensos.

## Atualização de 26/11/2026 — 14.2.5

[Homologação empresarial](HOMOLOGACAO-14.2.5.md): jornada completa em laboratório, backup/restauração de dados e sequências, parada e retomada controladas, recuperação administrativa local assinada e auditada, 516 testes backend, 250 frontend e onze verificações operacionais Windows. V11 aditiva; contas, senhas, anexo e histórico principais preservados. [Guia operacional](GUIA-OPERACIONAL.md) e [continuidade administrativa](CONTINUIDADE-ADMINISTRATIVA.md) atualizados. Ensaio com 2.011 chamados totais e até oito requisições simultâneas, sem extrapolar capacidade. A Etapa 14.2 está concluída nos limites do relatório; nenhuma nova política comercial, Etapa 15 ou chatbot foi implementado.
