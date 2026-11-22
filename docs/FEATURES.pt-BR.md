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

## 2. Perfis: Economia, Balanceado e Performance

**O que faz:** guarda configurações reutilizáveis e aplica a selecionada.

**Como funciona:**

- uma instalação nova cria três perfis base:
  - **Economia** — as faixas mais baixas, geradas a partir do mínimo do painel;
  - **Balanceado** — o perfil padrão, usando a maior taxa acima de 60 Hz quando existe;
  - **Performance** — mantém um repouso de 60 Hz ou mais;
- o perfil ativo é escolhido na Home;
- os perfis base podem ser editados e restaurados para o padrão do aparelho;
- perfis customizados podem ser criados, renomeados, editados e excluídos;
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
  em primeiro plano use **“Não usar Economia durante a economia de bateria”**;
- quando a economia desliga, o perfil selecionado volta;
- o comportamento pode ser desativado para usar sempre o perfil selecionado;
- enquanto Economia está ativa, o OneHZ desativa os limites de taxa da economia da
  Samsung e **restaura os valores originais** ao encerrar a sessão. No Xiaomi, a chave de
  taxa do HyperOS é salva e restaurada da mesma forma. Isso evita que as configurações do
  sistema fiquem alteradas após um crash ou desinstalação.

## 4. AOD: taxa com a tela desligada

**O que faz:** aplica uma taxa fixa enquanto a tela está desligada / Always On Display
está ativo.

**Como funciona:**

- a chave do AOD é independente dos perfis;
- o AOD sempre usa a **menor taxa exposta pela tela**;
- quando a tela desliga, timers e o monitor de toque param, então o aparelho entra no
  Doze normalmente;
- ao ligar a tela, o perfil ativo volta e o pulso de despertar é aplicado.

## 5. Brilho do AOD

**O que faz:** na Samsung, define o brilho do AOD a partir de uma porcentagem do brilho
da tela no momento do bloqueio, com uma faixa de segurança.

**Como funciona:**

- o app grava a chave de sistema `debug_aod_brightness` da Samsung (0–255);
- o serviço de AOD lê essa chave quando o AOD inicia;
- o OneHZ pré-grava o alvo enquanto a tela está acesa, então não precisa vencer a corrida
  durante o desligamento;
- uma escrita por transição; sem polling durante o AOD;
- uma faixa mínima/máxima mantém o AOD legível;
- “Restaurar brilho do sistema” remove a chave e devolve o controle ao sistema.

Este recurso é **somente Samsung** e fica oculto no Xiaomi, onde o brilho do AOD é
controlado pelo processo do próprio fabricante. Se você desinstalar o OneHZ, desative o
brilho do AOD antes. Se a chave ficar para trás, remova com:

```powershell
adb shell settings delete system debug_aod_brightness
```

## 6. Perfis por aplicativo

**O que faz:** atribui um perfil salvo a um aplicativo instalado.

**Como funciona:**

- um app referencia um dos perfis salvos (base ou customizado);
- o app em primeiro plano é consultado apenas quando existe pelo menos uma atribuição;
- no toque e no retorno ao app, o OneHZ pergunta ao serviço do Shizuku qual é o pacote em
  primeiro plano;
- não há polling periódico;
- editar o perfil referenciado atualiza todos os apps que o usam;
- excluir um perfil reatribui os apps dele para Balanceado;
- se o app não tem atribuição, o perfil ativo se aplica;
- durante a Economia automática, o app só mantém o perfil se
  **“Não usar Economia durante a economia de bateria”** estiver ligado.

## 7. Modo vídeo

**O que faz:** mantém a taxa de atualização no piso configurado no perfil ativo enquanto
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
- o piso de vídeo tem prioridade sobre o perfil enquanto a reprodução durar.

Os padrões ligam MediaSession, o veto de sessão pausada e a regra de decodificador; a
regra de áudio de mídia fica desligada. Cada regra pode ser alterada por perfil no editor.
A lista de apps forçados é global. **PiP ainda não é tratado** (veja o
[backlog](BACKLOG.md)).

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
  - **Balanceado**: máxima 2 s → maior acima de 60 Hz (ou 60) 5 s → mínima;
  - **Performance**: máxima 5 s → maior acima de 60 Hz 8 s → 60 Hz ou maior;
- o piso de vídeo é 30 Hz na Economia e 60 Hz no Balanceado/Performance;
- taxas não suportadas são rejeitadas no editor;
- no S25+ testado (10/20/24/27/30/40/48/60/80/120 Hz), os perfis gerados são Economia
  120 → 60 → 10, Balanceado 120 → 80 → 10 e Performance 120 → 80 → 60.

Usuários existentes mantêm as escadas Normal/ECO como Balanceado/Economia e recebem um
novo perfil Performance. As regras de vídeo antigas são preservadas, mas o mínimo de vídeo
segue os novos padrões 30/60/60.

## 10. Configuração guiada

- verificação de compatibilidade do fabricante/tela;
- status do Shizuku e pedido de permissão;
- isenção opcional de otimização de bateria;
- lista de permissão opcional do Doze (pelo Shizuku);
- permissão de notificação;
- notificação persistente do serviço em primeiro plano.

## 11. Sem wake locks

O serviço opcional em primeiro plano mantém o motor vivo sem segurar wake lock. O
aparelho continua entrando no Doze. Mudanças de estado da tela são tratadas por
broadcasts do sistema, não por polling.

## 12. Diagnóstico

- log em memória (até 500 entradas), não enviado e não persistido;
- filtros por tag e texto;
- estado do motor, Hz aplicado/observado, última escrita, detalhe de vídeo, estado do
  monitor de toque, brilho/modo da tela, estado da economia de bateria e a rota de taxa
  ativa;
- botões de teste para a primeira faixa/repouso e um ciclo completo;
- ações de segurança “Verificar controle da tela” e “Restaurar brilho do sistema”;
- relatório de diagnóstico pode ser copiado manualmente para um relato de bug.

## 13. Aparência

Temas sistema, claro, escuro e AMOLED. O AMOLED usa preto puro em painéis OLED.

## 14. Backup e restauração

**O que faz:** exporta e importa a configuração de comportamento completa.

**Como funciona:**

- Ajustes → **Backup e restauração** grava um JSON versionado pelo seletor de arquivos do
  sistema, sem permissão de armazenamento;
- o arquivo inclui todos os perfis, o perfil ativo, atribuições de apps, vídeo, AOD,
  switch mestre e a lista global de apps forçados como vídeo;
- tema, idioma, diagnóstico, cache de atualização e snapshots internos de restauração do
  sistema não entram;
- a importação substitui a configuração atual depois de uma confirmação;
- perfis com taxas não suportadas pela tela atual são adaptados para a taxa mais próxima,
  e o resumo informa quantos foram adaptados;
- atribuições para um perfil ausente caem em Balanceado.

## 15. Idiomas

Português (Brasil), inglês e espanhol. Veja o
[status das traduções](TRANSLATIONS.md).
