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

## 2. Perfil normal

**O que faz:** é o perfil usado quando a economia de bateria do sistema **não** está
ativa.

**Como funciona:** é independente do perfil ECO e pode ser desativado sozinho. Quando
desativado, o OneHZ para de controlar a taxa nessa situação e devolve o comportamento
adaptativo do sistema.

O perfil normal padrão é **máximo do aparelho → 60 Hz → mínimo do aparelho**, segurando
cada nível por **2 s e 5 s**.

## 3. Perfil ECO (economia de bateria)

**O que faz:** é o perfil usado enquanto a economia de bateria do sistema está ativa.

**Como funciona:**

- o app escuta o broadcast de economia do sistema e reaplica a política;
- o perfil ECO padrão começa em 60 Hz (ou na taxa suportada mais próxima abaixo disso),
  segura por 2 s e depois usa o mínimo do aparelho;
- um app pode optar por **“Ignorar ECO”**, mantendo o próprio perfil/normal enquanto a
  economia está ativa;
- enquanto o ECO está ativo, o OneHZ desativa os limites de taxa da economia da Samsung e
  **restaura os valores originais** ao encerrar a sessão. No Xiaomi, a chave de taxa do
  HyperOS é salva e restaurada da mesma forma. Isso evita que as configurações do sistema
  fiquem alteradas após um crash ou desinstalação.

## 4. AOD: taxa com a tela desligada

**O que faz:** aplica uma taxa fixa dedicada enquanto a tela está desligada / Always On
Display está ativo.

**Como funciona:**

- a chave do AOD é independente dos perfis normal e ECO;
- o padrão é 30 Hz (o padrão do sistema);
- quando a tela desliga, timers e o monitor de toque param, então o aparelho entra no
  Doze normalmente;
- ao ligar a tela, a escada normal volta e o pulso de despertar é aplicado.

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

**O que faz:** atribui um perfil a um aplicativo instalado.

**Como funciona:**

- o app pode começar pelo perfil normal, pelo perfil ECO ou por um perfil personalizado;
- o app em primeiro plano é consultado apenas quando existe pelo menos um perfil por
  aplicativo;
- no toque e no retorno ao app, o OneHZ pergunta ao serviço do Shizuku qual é o pacote em
  primeiro plano;
- não há polling periódico;
- se o app não tem perfil, a política normal/ECO se aplica.

## 7. Modo vídeo

**O que faz:** mantém a taxa de atualização em um piso configurável enquanto um vídeo
está tocando.

**Como funciona:**

- o app em primeiro plano precisa manter um decodificador de vídeo no Codec2;
- uma MediaSession que existe mas está pausada é autoritativa: não é tratada como
  reprodução;
- sem MediaSession, um player de áudio `USAGE_MEDIA` iniciado confirma a reprodução;
- apps somente de áudio, a câmera e jogos não mantêm decodificador de vídeo, então não
  acionam o piso;
- uma sondagem roda ~1,8 s depois de soltar o dedo e uma rechecagem lenta roda a cada 12 s
  enquanto um vídeo está ativo;
- o piso de vídeo tem prioridade sobre o perfil do app/normal/ECO enquanto a reprodução
  durar.

Cada regra acima pode ser ativada ou desativada em **Modo vídeo → Configurar detecção**,
para apps que informam sessões ou decodificadores de forma diferente. A mesma tela guarda
uma lista de apps que devem ser sempre tratados como vídeo em primeiro plano, mesmo sem
sessão, decodificador ou áudio. Os padrões reproduzem o comportamento original.
**PiP ainda não é tratado** (veja o [backlog](BACKLOG.md)).

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
- o perfil normal padrão é **máximo do painel → 60 Hz → mínimo do painel**, com esperas
  de 2 s e 5 s (no S25+ testado: 120 → 60 → 10);
- o ECO padrão começa em 60 Hz, segura por 2 s e depois usa o mínimo do painel;
- taxas não suportadas são rejeitadas no editor.

Os padrões são criados **somente em uma instalação nova**. Perfis existentes são mantidos
ao atualizar, inclusive ao instalar esta beta sobre a 1.0.1.

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

## 14. Idiomas

Português (Brasil), inglês e espanhol. Veja o
[status das traduções](TRANSLATIONS.md).

## 15. Sono (Doze forçado)

Opcional e desligado por padrão. Ao apagar a tela, o OneHZ pede ao sistema para entrar em
Doze na hora, em vez de esperar os temporizadores de inatividade:

- Doze profundo (padrão) ou leve;
- condições avaliadas só no momento em que a tela apaga: pausa de uma hora, janela de
  horário que pode cruzar a meia-noite, carregamento e música em segundo plano;
- `unforce` ao acender a tela, ao desligar o recurso e depois de crash ou reboot;
- verificação pelo estado real de device-idle, com a mensagem explícita "a ROM não entrou
  em Doze" em vez de um falso sucesso;
- lista de apps que podem acordar o aparelho, gravada na whitelist de Doze pelo Shizuku;
- medições locais: tempo com a tela apagada e em Doze nas últimas 24h, último descanso
  (30 min ou mais), nível de bateria e %/h por sessão.

Nenhum wake lock é mantido e nada é consultado por polling. As medições são locais e
identificadas como medidas pelo OneHZ; incluem o tempo de AOD. Veja [SLEEP.md](SLEEP.md).
