# Escala Foto — protótipo Android

Primeira versão funcional do projeto de calendário de trabalho por foto.

## Já implementado
- Interface Android em Jetpack Compose.
- Painel com próximo plantão, próxima folga e resumo do mês.
- Calendário mensal.
- Importação de foto/print.
- OCR local com Google ML Kit.
- Leitura inicial de datas, horários, folgas e algumas tabelas simples.
- Campo opcional para localizar o nome do funcionário.
- Tela para conferir e corrigir horários antes de salvar.
- Armazenamento local.
- Cálculo de horas e estimativa de ganhos.
- Compartilhamento da escala em texto.
- Build automático do APK pelo GitHub Actions.

## Próximas evoluções
1. Melhorar leitura de tabelas complexas e escalas de WhatsApp.
2. Detectar automaticamente 12x36, 24x72, 6x1 e 5x2.
3. Exportar em lote para Google Agenda/ICS.
4. Notificações antes do plantão.
5. Múltiplos empregos.
6. Plano grátis/Pro.
7. Testes com escalas reais e ajuste do OCR.

> Nome e identidade visual ainda são provisórios. O foco desta fase é provar que a importação por imagem funciona de verdade.

## Estado da build
Workflow preparado para gerar o APK debug automaticamente a cada atualização do protótipo.
