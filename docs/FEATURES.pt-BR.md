# Funcionalidades

[English](FEATURES.md) · **Português (Brasil)**

Esta página descreve o que cada recurso do OneHZ faz e como ele se comporta. Para
instalar e atualizar, veja [INSTALAÇÃO](INSTALL.pt-BR.md); para aparelhos e limitações,
veja [COMPATIBILIDADE](COMPATIBILITY.pt-BR.md).

Todos os perfis só são aplicados enquanto o OneHZ está ativo e conectado ao Shizuku.

---

## 1. Escada de taxa de atualização (1 a 5 faixas)

**O que faz:** cada perfil tem de uma a cinco faixas. Cada faixa tem uma frequência e,
exceto a última, um tempo de espera.

**Como funciona:**

- um toque aplica a primeira faixa imediatamente;
- cada tempo de espera termina e a próxima faixa é aplicada;
- a última faixa é o repouso e não tem limite de tempo;
- um perfil com uma faixa só funciona como taxa fixa e não inicia o monitor de toque;
- as faixas precisam ser decrescentes (por exemplo 120 → 60 → 30);
- os tempos variam de 0,5 s a 60 s em passos de 0,5 s;
- enquanto o dedo está na tela, a descida é adiada (até 20 rechecagens de 100 ms) para
  a interface não cair durante a interação.

## 2. Perfis: Economia, Equilibrado e Desempenho

**O que faz:** guarda configurações reutilizáveis e aplica a selecionada.

**Como funciona:**

- uma instalação nova cria três perfis base:
  - **Economia** — as faixas mais baixas, geradas a partir do mínimo do painel;
  - **Equilibrado** — o perfil padrão, usando a maior taxa acima de 60 Hz quando existe;
  - **Desempenho** — mantém um repouso de 60 Hz ou mais;
- o perfil ativo é escolhido na Home; o card mostra o perfil **selecionado** e, quando forem diferentes, o perfil **em uso**;
- o card do perfil na Home tem o atalho **Automatizar este perfil**, que abre as regras de ativação;
- os perfis base podem ser editados e restaurados para o padrão do aparelho;
- perfis customizados podem ser criados, renomeados, editados, duplicados e excluídos;
- um perfil excluído pode ser restaurado pela ação curta **Desfazer**, incluindo as
  atribuições de apps que ele tinha;
- cada perfil guarda suas próprias configurações de vídeo;
- perfis por app referenciam um perfil salvo em vez de copiar a escada.

Em uma tela com apenas duas taxas, os três perfis usam as mesmas duas faixas e diferem
apenas no tempo na máxima. O tempo na mínima é o repouso até o próximo toque, então há
apenas uma espera ajustável nesses painéis.

## 3. Economia de bateria e o perfil ativo

**O que faz:** mantém o comportamento atual de trocar para Economia enquanto a economia
de bateria do sistema está ativa.

**Como funciona:**

- o app escuta o broadcast de economia do sistema e reaplica a política;
- enquanto a economia está ligada, Economia é aplicada automaticamente, a menos que o app
  em primeiro plano use **“Manter o perfil deste app durante a economia de bateria”**;
- quando a economia desliga, o perfil selecionado volta;
- o comportamento pode ser desativado em **Gerenciar perfis → Economia automática**, para usar sempre o perfil selecionado;
- enquanto Economia está ativa, o OneHZ desativa os limites de taxa da economia da
  Samsung e **restaura os valores originais** ao encerrar a sessão. No Xiaomi, a chave de
  taxa do HyperOS é salva e restaurada da mesma forma. Isso evita que as configurações do
  sistema fiquem alteradas após um crash ou desinstalação.

## 4. Taxa no AOD

**O que faz:** aplica uma taxa fixa enquanto a tela está desligada / Always On Display
está ativo.

**Chave principal.** O switch do AOD na tela inicial é o único master desta seção.
Desligá-lo pausa a taxa e o brilho sem redefinir as escolhas. A tela de detalhe do AOD
não repete o master. O switch de taxa está disponível em todos os aparelhos suportados;
somente o brilho depende do fabricante.

**Como funciona:**

- o master do AOD é independente dos perfis;
- o AOD sempre usa a **menor taxa exposta pela tela**;
- quando a tela desliga, timers e o monitor de toque param, então o aparelho entra no
  Doze normalmente;
- ao ligar a tela, o perfil ativo volta e o pulso de despertar é aplicado.

## 5. Brilho do AOD

**O que faz:** na Samsung, define o brilho do AOD a partir de uma porcentagem do brilho
da tela no momento do bloqueio, com uma faixa de segurança.

**Independente da taxa.** O brilho tem o próprio switch e continua disponível quando a
taxa está desligada. Ele ainda obedece ao master do AOD na tela inicial.

**Como funciona:**

- o app grava a chave de sistema `debug_aod_brightness` da Samsung (0–255);
- o serviço de AOD lê essa chave quando o AOD inicia;
- o OneHZ pré-grava o alvo enquanto a tela está acesa, então não precisa vencer a corrida
  durante o desligamento;
- uma escrita por transição; sem polling durante o AOD;
- uma faixa mínima/máxima mantém o AOD legível;
- “Restaurar brilho do sistema” remove a chave e devolve o controle ao sistema.

Este recurso é **somente Samsung**. No Xiaomi, onde o processo do fabricante controla
o brilho do AOD, a linha aparece como um switch desabilitado explicando que o painel
cuida disso. Se você desinstalar o OneHZ, desative o
brilho do AOD antes. Se a chave ficar para trás, remova com:

```powershell
adb shell settings delete system debug_aod_brightness
```

## 6. Perfis por aplicativo

**O que faz:** atribui um perfil salvo a um aplicativo instalado.

**Como funciona:**

- um app referencia um dos perfis salvos (base ou personalizado);
- a linha do app e a folha de atribuição dizem: “Este app usa o perfil X. Editar esse perfil muda todos os apps que o usam.”;
- o app em primeiro plano é consultado apenas quando existe pelo menos uma atribuição;
- no toque e no retorno ao app, o OneHZ pergunta ao serviço do Shizuku qual é o pacote em
  primeiro plano;
- não há polling periódico;
- editar o perfil referenciado atualiza todos os apps que o usam;
- excluir um perfil reatribui os apps dele para Equilibrado;
- se o app não tem atribuição, o perfil ativo se aplica;
- a exceção aparece tanto ao adicionar o app quanto ao editar a atribuição, como
  **“Manter o perfil deste app durante a economia de bateria”**;
- durante a Economia automática, o app só mantém o perfil se essa exceção estiver ligada;
- a exceção fica desativada para o perfil Economia, porque não há um perfil próprio para
  manter.

## 7. Modo vídeo

**O que faz:** mantém a taxa de atualização no mínimo configurado no perfil ativo enquanto
um vídeo está tocando.

**Como funciona:**

- o app em primeiro plano precisa manter um decodificador de vídeo no Codec2;
- uma MediaSession que existe mas está pausada é autoritativa: não é tratada como
  reprodução;
- sem MediaSession, um player de áudio `USAGE_MEDIA` iniciado confirma a reprodução;
- apps somente de áudio, a câmera e jogos não mantêm decodificador de vídeo, então não
  acionam o piso;
- uma sondagem roda ~1,8 s depois de soltar o dedo e uma rechecagem lenta roda a cada 12 s
  enquanto um vídeo está ativo;
- o mínimo de vídeo tem prioridade sobre o perfil enquanto a reprodução durar;
- a lista de apps forçados é global e vale para todos os perfis, mas as regras e o mínimo ficam em cada perfil.

Os padrões ligam MediaSession, o veto de sessão pausada e a regra de decodificador; a
regra de áudio de mídia fica desligada. Cada regra pode ser alterada por perfil em uma
sheet de detecção dedicada; o card mostra a contagem de regras ativas e avisa quando
nenhum sinal positivo está ativo. A lista de apps forçados é global e abre na mesma
sheet. Uma janela picture-in-picture é detectada pela tarefa fixada e mantém o piso de vídeo enquanto estiver visível.

## 8. Pulso de despertar

**O que faz:** evita renderizar a tela de bloqueio e a animação de desbloqueio na taxa de
repouso.

**Como funciona:** ao ligar/destravar, a primeira faixa é aplicada por cerca de um
segundo. O pulso tem debounce de 1,2 s porque `SCREEN_ON` e `USER_PRESENT` normalmente
chegam juntos.

## 9. Padrões por dispositivo

**O que faz:** cria perfis coerentes em uma instalação nova.

**Como funciona:**

- lê as taxas do painel da lista estática da Samsung ou do fallback
  `Display.supportedModes` no Xiaomi;
- em uma tela com duas taxas, os três perfis usam a máxima e a mínima e diferem apenas no
  tempo na máxima (1 s / 2 s / 5 s);
- em uma tela com mais taxas:
  - **Economia**: máxima 1 s → 60 Hz (ou a maior abaixo de 60) 6 s → mínima;
  - **Equilibrado**: máxima 2 s → maior acima de 60 Hz (ou 60) 5 s → mínima;
  - **Desempenho**: máxima 5 s → maior acima de 60 Hz 8 s → 60 Hz ou maior;
- o mínimo de vídeo é 30 Hz na Economia e 60 Hz no Equilibrado/Desempenho;
- taxas não suportadas são rejeitadas no editor;
- no S25+ testado (10/20/24/27/30/40/48/60/80/120 Hz), os perfis gerados são Economia
  120 → 60 → 10, Equilibrado 120 → 80 → 10 e Desempenho 120 → 80 → 60.

Usuários existentes mantêm as escadas Normal/ECO como Equilibrado/Economia e recebem um
novo perfil Desempenho. As regras de vídeo antigas são preservadas, mas o mínimo de vídeo
segue os novos padrões 30/60/60.

## 10. Configuração guiada

- verificação de compatibilidade do fabricante/tela;
- status do Shizuku e pedido de permissão;
- isenção de otimização de bateria;
- lista de permissão do Doze (pelo Shizuku);
- permissão de notificação;
- notificação persistente do serviço em primeiro plano;
- tile opcional nas Configurações Rápidas, mostrado como **Opcional** ou **Adicionado**.

A etapa opcional oferece apenas o tile. O Uso tem aba própria na barra inferior, e as
regras de ativação e o Diagnóstico ficam em Ajustes. Nenhuma ação opcional conclui a
configuração; somente **Concluir e ativar** faz isso.

## 11. Sem wake locks

O serviço opcional em primeiro plano mantém o motor vivo sem segurar wake lock. O
aparelho continua entrando no Doze. Mudanças de estado da tela são tratadas por
broadcasts do sistema, não por polling.

## 12. Diagnóstico

Ajustes → Sistema → **Diagnóstico**.

- log em memória (até 500 entradas), não enviado e não persistido;
- filtros por tag e texto;
- estado do motor, Hz aplicado/observado, última escrita, detalhe de vídeo, estado do
  monitor de toque, brilho/modo da tela, estado da economia de bateria e a rota de taxa
  ativa;
- um card **Testes** no topo do resumo executa os testes de primeira faixa/repouso e ciclo
  completo, além do teste de Doze; com o Sono desligado, o teste oferece **Abrir Sono**;
- ações de segurança “Verificar controle da tela” e “Restaurar brilho do sistema”;
- relatório de diagnóstico pode ser copiado manualmente para um relato de bug.

## 13. Aparência

Temas sistema, claro, escuro e AMOLED. O AMOLED usa preto puro em painéis OLED.

## 14. Backup e restauração

**O que faz:** exporta e importa a configuração de comportamento completa.

**Como funciona:**

- Ajustes → Dados → **Backup e restauração** grava um JSON versionado pelo seletor de
  arquivos do sistema, sem permissão de armazenamento;
- o arquivo inclui todos os perfis, o perfil ativo, atribuições de apps e os alvos delas,
  regras de ativação, vídeo, AOD, configurações do Sono, switch mestre e a lista global de
  apps forçados como vídeo;
- os alvos por app usam o schema 2 de perfis, que ainda lê arquivos schema 1, e o backup
  mantém a mesma compatibilidade;
- a lista de exceções do Doze, as medições e o estado de pausa do Sono, além da fixação da
  notificação, são estado do sistema/transitório e não entram;
- um backup criado antes de o Sono existir **não** altera as configurações atuais do Sono na importação;
- tema, idioma, diagnóstico, cache de atualização e snapshots internos de restauração do
  sistema não entram;
- a importação substitui a configuração atual depois de uma confirmação;
- perfis com taxas não suportadas pela tela atual são adaptados para a taxa mais próxima,
  e o resumo informa quantos foram adaptados;
- atribuições para um perfil ausente caem em Equilibrado.

## 15. Idiomas

Português (Brasil), inglês e espanhol. Veja o
[status das traduções](TRANSLATIONS.md).

## 16. Sono (Doze forçado)

Opcional e desligado por padrão. O switch do Sono na tela inicial é o único master. Com o
Sono desligado, o detalhe mantém as opções de comportamento e a lista de apps que acordam
visíveis e editáveis, e mostra o resumo compacto das medições; apenas pausar/retomar exige
o Sono ligado. O teste de Doze fica no Diagnóstico, não nesta tela. O detalhe mostra uma
nota compacta quando o Sono está desligado, uma linha de status só para estados de
ativo/pausado/falha e um aviso dispensável dentro do card "Como funciona".

Ao apagar a tela, o OneHZ pede ao sistema para entrar em
Doze na hora, em vez de esperar os temporizadores de inatividade. O primeiro enable pede
confirmação porque notificações e chamadas por internet podem atrasar:

- Doze profundo (padrão) ou leve;
- condições reavaliadas com a tela apagada: pausa de uma hora, janela de horário que pode
  cruzar a meia-noite, carregamento e reprodução de música;
- um switch **Doze durante o carregamento** (ligado por padrão) que mantém o Doze na
  tomada; desligue para pausar enquanto carrega;
- um switch **Doze com música** (desligado por padrão) para manter o Doze durante o áudio;
- `unforce` ao acender a tela, ao desligar o recurso e depois de crash ou reboot;
- verificação pelo estado real de device-idle, com a mensagem explícita "a ROM não entrou
  em Doze" em vez de um falso sucesso;
- lista de apps que podem acordar o aparelho, gravada nas exceções do Doze pelo Shizuku;
- medições locais: o detalhe mostra um resumo compacto (tela apagada, Doze e última
  sessão); as linhas completas de 24h e o histórico de sessões ficam na aba Uso.

Nenhum wake lock é mantido e nada é consultado por polling. As medições são locais e
identificadas como medidas pelo OneHZ; incluem o tempo de AOD. O detalhe aponta para a aba
Uso para o detalhamento completo. Veja [SLEEP.md](SLEEP.md).

## 17. Controles rápidos

**Pausa.** Ao lado da chave principal, **Pausar por 1 hora** interrompe o controle e
restaura os valores do sistema. O card mostra o horário de retorno e a ação **Retomar
agora**. A pausa tem prazo, sobrevive a um reinício e o motor volta sozinho.

**Tile nas Configurações Rápidas.** O tile do OneHZ liga e desliga o app; pausar e retomar
ficam na notificação, na Home e no atalho do app. Ajustes → Controles rápidos tem a ação
**Adicionar tile nas Configurações Rápidas**, que pede ao sistema para colocá-lo. O tile
indica quando o Shizuku está indisponível.

**Notificação.** A notificação do serviço em primeiro plano mostra o perfil ativo e a taxa
atual, e oferece **Pausar por 1 hora** / **Retomar agora** e **Desativar**. Em Ajustes →
Controles rápidos, **Mostrar frequência** pode ocultar a taxa e deixar só o perfil. Com a
taxa ligada, as atualizações são agrupadas em no máximo uma por segundo; mudanças de estado
(pausa, AOD, inativo, perfil) são imediatas. Em AOD a notificação mostra **AOD** em vez de
“Inativo”.

**Acesso às notificações.** Ajustes → Controles rápidos → **Notificações** mostra se o OneHZ
pode publicar a notificação e abre a permissão ou a página do sistema. **Fixar notificação**
e **Mostrar frequência** só funcionam com as notificações habilitadas; ao desativá-las, o pin
salvo é desligado. O serviço em primeiro plano continua rodando de qualquer forma.

**Fixar notificação.** Ajustes → Controles rápidos → **Fixar notificação** vem desligado.
Quando está desligado, a notificação pode ser dispensada com o deslize e fica oculta até o
OneHZ ser aberto de novo. Quando está ligado, uma confirmação explica que o Android 14 ou
mais recente ainda permite o deslize e o OneHZ restaura a notificação na hora; isso não
mantém o serviço vivo nem muda o consumo de bateria. A opção pode ser desligada no mesmo
lugar a qualquer momento.

**Otimização de bateria.** Com as notificações ligadas e o OneHZ ainda otimizado pelo
sistema, Ajustes → Controles rápidos mostra um aviso de que as atualizações da notificação
podem atrasar em segundo plano. **Permitir** abre o diálogo de isenção do sistema; a primeira
visita também pergunta uma única vez com a mesma ação, e **Agora não** mantém só o aviso. O
aviso desaparece assim que o app fica isento. No HyperOS, deixe também **Sem restrições** e
ative a **inicialização automática**, porque o gerenciador de energia da fabricante não é
coberto pela isenção padrão. A isenção é opcional: o OneHZ funciona sem ela.

**Atalhos do app.** Pressione e segure o ícone na tela inicial para **Pausar 1 h**,
**Economia** e **Diagnóstico**.

Nenhum desses controles faz polling, mantém wake lock ou adiciona permissão nova.

## 18. Alterações no sistema e relatório do painel

Ajustes → Sistema.

**Alterações no sistema** lista as chaves que o OneHZ pode gravar pelo Shizuku, o valor
atual e o valor original quando a sessão guardou um backup. Na Samsung aparecem as chaves
de taxa, a chave de brilho do AOD e as chaves de economia de bateria; no HyperOS aparece a
chave de taxa ativa. A tela também informa se o OneHZ está nas exceções do Doze.

**Restaurar tudo** desliga a chave principal, para o motor e restaura os valores capturados
de taxa, economia de bateria e brilho do AOD. Perfis, atribuições e configurações do Sono
continuam salvos, então é seguro ligar o OneHZ de novo depois.

**Alterações externas.** Se outro app reescrever uma chave, a auditoria da sessão reaplica
os valores do OneHZ e registra o evento; a tela mostra um aviso até ser aberta novamente.

**Relatório do painel** exporta um arquivo de texto com versão do app, aparelho, fabricante,
taxas do painel, veredito da checagem, estado do Shizuku, exceção do Doze e as chaves
acima. Não inclui lista de apps instalados nem dados pessoais.

## 19. Uso

O Uso é a quarta aba da barra inferior (Início, Apps, Uso, Ajustes), então os dados
quantitativos ficam a um toque. A tela mostra quanto tempo o OneHZ passou em cada taxa hoje
e nos últimos sete dias, além do tempo de Always On Display, do tempo sem controle e da
divisão por perfil. Ela também reúne o resumo de Sono/Doze e o histórico de sessões, para
os dados quantitativos ficarem em um lugar só. O detalhe do Sono mantém um resumo compacto
e aponta para cá.

A aba atualiza ao voltar para ela, a cada 30 segundos enquanto está visível e pelo botão de
atualizar. Sem dados, mostra um único estado vazio; quando o OneHZ está desligado, esse
estado oferece **Ativar OneHZ**.

Os dados de taxa vêm só de transições do motor, ficam locais em buckets diários e são
apagados após 30 dias. Nada é enviado e não há atribuição por app, que exigiria consultar
o app em primeiro plano o tempo todo. Os números de Doze são os mesmos registros locais da
tela Sono.

## 20. Regras de ativação

Ajustes → Automação → **Regras de ativação**. O card do perfil na Home abre a mesma tela
por **Automatizar este perfil**. Regras opcionais podem trocar para um perfil salvo conforme
o ambiente. As regras nunca copiam um perfil, então editar o perfil muda todas as regras e
apps que apontam para ele.

- **Proteção de bateria baixa.** Usa um perfil no limite configurável ou abaixo dele
  (5-50%, passo 5). Uma histerese de 4 pontos evita oscilar em volta do limite.
- **Enquanto carrega.** Usa um perfil enquanto o carregador está conectado.
- **Janela de horário.** Usa um perfil dentro de uma janela que pode atravessar a
  meia-noite.

Precedência: pausa e AOD primeiro, depois a proteção de bateria, a janela, o carregador, a
atribuição do app em primeiro plano e, por fim, o perfil selecionado. A camada ativa aparece
no Diagnóstico e o tempo entra em Uso.

## 21. Alvos por app

Uma atribuição de app pode apontar para um perfil salvo, fixar uma taxa ou devolver o
controle ao sistema. A taxa fixa trava a atualização enquanto o app está em primeiro plano e
ignora o piso de vídeo; o alvo "padrão do sistema" libera os valores do OneHZ para aquele
app. Editar um perfil referenciado continua mudando todos os apps que apontam para ele, e o
backup mantém o tipo de alvo. O schema 2 de perfis lê arquivos schema 1 e o backup mantém a
mesma compatibilidade.

## 22. Automação

O OneHZ aceita broadcasts explícitos para ferramentas de automação. As ações mapeiam ações
que já existem no app e não retornam dados:

```text
io.github.nevesz.onehz.action.PAUSE_TIMED   extra: minutes (1-1440, padrão 60)
io.github.nevesz.onehz.action.RESUME
io.github.nevesz.onehz.action.SET_PROFILE   extra: profileId
io.github.nevesz.onehz.action.TOGGLE
```

Envie um broadcast explícito para `io.github.nevesz.onehz` (por exemplo com `am broadcast
-a ... -n io.github.nevesz.onehz/.engine.OneHzActionReceiver`). O receiver só seleciona
perfis salvos e estado de pausa/alternância; não há operação privilegiada.

## 23. Agenda noturna do AOD (Samsung)

Em telas Samsung com suporte a brilho do AOD, uma janela noturna pode usar outro brilho
dentro da faixa de segurança. O valor é escolhido quando a tela apaga, usa a mesma faixa e
é limitado como o percentual base. Mudar a janela vale no próximo bloqueio; nada é gravado
com a tela desligada.
