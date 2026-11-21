# OneHZ

[English](README.md) · **Português (Brasil)**

[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B-green.svg)](https://developer.android.com)
[![Sem root](https://img.shields.io/badge/root-n%C3%A3o%20necess%C3%A1rio-brightgreen.svg)](#requisitos)
[![Sem telemetria](https://img.shields.io/badge/telemetria-nenhuma-brightgreen.svg)](PRIVACY.md)
[![GitHub Sponsors](https://img.shields.io/badge/patrocine-GitHub%20Sponsors-ea4aaa.svg)](https://github.com/sponsors/neveSZ)
[![LivePix (Brasil)](https://img.shields.io/badge/apoio-LivePix%20Brasil-ff69b4.svg)](https://livepix.gg/onehz)

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https%3A%2F%2Fgithub.com%2FneveSZ%2Fonehz"><img src="docs/assets/obtainium-badge.png" alt="Obter no Obtainium" height="48"></a>

O OneHZ é um controlador de taxa de atualização para **telas Samsung e
Xiaomi/Redmi/POCO**. Ele
altera a taxa de atualização **exclusivamente pelo Shizuku** — sem root, sem ADB direto
pelo app, sem `WRITE_SECURE_SETTINGS` e **sem AccessibilityService**.

> **Projeto pessoal.** Criei o OneHZ para o meu uso diário. Não ofereço garantia de
> manutenção, mas estou aberto a relatos de bugs e sugestões. Use por sua conta e risco.
> Todas as funcionalidades são gratuitas e de código aberto; doações nunca desbloqueiam
> nada.

---

## Status do projeto

- Versão estável mais recente: **1.0.1**.
- Testado em um Samsung Galaxy S25+ (`SM-S936B`) e em um POCO `2412DPC0AG` (`rodin`).
- Funciona **apenas em Samsung ou Xiaomi/Redmi/POCO** com tela que exponha **pelo
  menos duas taxas de atualização**. Outras marcas são bloqueadas e o app falha fechado
  (não altera nada). **LTPO não é obrigatório.**
- Futuras versões beta serão publicadas como pré-releases.

## Aparelho e software testados

| Item | Valor |
|---|---|
| Aparelho | Samsung Galaxy S25+ (`SM-S936B`, produto `pa2q`) |
| Android | 17 (API 37) |
| One UI | 9.0 (`ro.build.version.oneui=90000`) |
| Build | `S936BXXUDDZJB` |
| Patch de segurança | 2026-10-05 |
| Resolução testada | 1080 × 2340 (FHD+) |
| Taxas expostas pelo sistema | 120, 80, 60, 48, 40, 30, 26,67, 24, 20, 10 Hz |
| Shizuku | fork `13.7.0-thedjchi` |
| OneHZ | 1.0.1 |

**O que não foi testado:** outros modelos Samsung, outras versões de Android/One UI,
WQHD+, dobráveis, tela de capa, tablets e outras marcas. Podem funcionar, mas não posso
garantir.

## Requisitos

- Samsung **ou** Xiaomi/Redmi/POCO (HyperOS/MIUI).
- Tela que exponha **pelo menos duas taxas de atualização** (por exemplo 120/90/60/30).
  LTPO não é obrigatório.
- **Android 12 (API 31) ou superior** (`minSdk 31`; Android 16 e 17 foram testados).
- **Shizuku instalado e em execução.**
- O OneHZ é distribuído como APK assinado no GitHub Releases. Não está no Google Play.
- O [Obtainium](https://obtainium.imranr.dev/) é suportado para atualizações automáticas.

### Recomendação de Shizuku

Testei o OneHZ com o **fork do thedjchi `v13.7.0-thedjchi`**:
<https://github.com/thedjchi/Shizuku/tree/v13.7.0-thedjchi>

O Shizuku oficial deve funcionar, mas não foi testado. Se quiser reproduzir meu
ambiente, use a mesma versão do fork.

### Sem serviço de acessibilidade

O OneHZ deliberadamente **não** usa `AccessibilityService`. Serviços de acessibilidade
podem interferir em apps bancários e outros aplicativos sensíveis. Todo o acesso
privilegiado passa pelo Shizuku, e o app falha fechado quando o Shizuku não está
disponível.

---

## Funcionalidades

### 1. Escada de taxa de atualização (1 a 5 faixas)

**O que faz:** cada perfil tem de uma a cinco faixas. Cada faixa tem uma frequência e,
exceto a última, um tempo de espera.

**Como funciona:**

- um toque aplica a primeira faixa imediatamente;
- cada tempo de espera termina e a próxima faixa é aplicada;
- a última faixa é o repouso e não tem limite de tempo;
- um perfil com uma faixa só funciona como taxa fixa e não inicia o monitor de toque;
- as faixas precisam ser decrescentes (por exemplo 120 → 60 → 10);
- os tempos variam de 0,5 s a 60 s em passos de 0,5 s;
- enquanto o dedo está na tela, a descida é adiada (até 20 rechecagens de 100 ms) para
  a interface não cair durante a interação.

### 2. Perfil normal

**O que faz:** é o perfil usado quando a economia de bateria do sistema **não** está
ativa.

**Como funciona:** é independente do perfil ECO e pode ser desativado sozinho. Quando
desativado, o OneHZ para de controlar a taxa nessa situação e devolve o comportamento
adaptativo do sistema.

O perfil normal padrão é **máximo do aparelho → 60 Hz → mínimo do aparelho**, segurando
cada nível por **2 s e 5 s**.

### 3. Perfil ECO (economia de bateria)

**O que faz:** é o perfil usado enquanto a economia de bateria do sistema está ativa.

**Como funciona:**

- o app escuta o broadcast de economia do sistema e reaplica a política;
- o perfil ECO padrão começa em 60 Hz (ou na taxa suportada mais próxima abaixo disso),
  segura por 2 s e depois usa o mínimo do aparelho;
- um app pode optar por **“Ignorar ECO”**, mantendo o próprio perfil/normal enquanto a
  economia está ativa;
- enquanto o ECO está ativo, o OneHZ desativa os limites de taxa da economia da Samsung e
  **restaura os valores originais** ao encerrar a sessão. Isso evita que as configurações
  do sistema fiquem alteradas após um crash ou desinstalação.

### 4. AOD: taxa com a tela desligada

**O que faz:** aplica uma taxa fixa dedicada enquanto a tela está desligada / Always On
Display está ativo.

**Como funciona:**

- a chave do AOD é independente dos perfis normal e ECO;
- o padrão é 30 Hz (o padrão do sistema);
- quando a tela desliga, timers e o monitor de toque param, então o aparelho entra no
  Doze normalmente;
- ao ligar a tela, a escada normal volta e o pulso de despertar é aplicado.

### 5. Brilho do AOD

**O que faz:** define o brilho do AOD a partir de uma porcentagem do brilho da tela no
momento do bloqueio, com uma faixa de segurança.

**Como funciona:**

- o app grava a chave de sistema `debug_aod_brightness` da Samsung (0–255);
- o serviço de AOD lê essa chave quando o AOD inicia;
- o OneHZ pré-grava o alvo enquanto a tela está acesa, então não precisa vencer a corrida
  durante o desligamento;
- uma escrita por transição; sem polling durante o AOD;
- uma faixa mínima/máxima mantém o AOD legível;
- “Restaurar brilho do sistema” remove a chave e devolve o controle ao sistema.

> Se você desinstalar o OneHZ, desative o brilho do AOD antes. Se a chave ficar para trás,
> remova com:
>
> ```powershell
> adb shell settings delete system debug_aod_brightness
> ```

### 6. Perfis por aplicativo

**O que faz:** atribui um perfil a um aplicativo instalado.

**Como funciona:**

- o app pode começar pelo perfil normal, pelo perfil ECO ou por um perfil personalizado;
- o app em primeiro plano é consultado apenas quando existe pelo menos um perfil por
  aplicativo;
- no toque e no retorno ao app, o OneHZ pergunta ao serviço do Shizuku qual é o pacote em
  primeiro plano;
- não há polling periódico;
- se o app não tem perfil, a política normal/ECO se aplica.

### 7. Modo vídeo

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
**PiP ainda não é tratado** (veja o backlog).

### 8. Pulso de despertar

**O que faz:** evita renderizar a tela de bloqueio e a animação de desbloqueio na taxa de
repouso.

**Como funciona:** ao ligar/destravar, a primeira faixa é aplicada por cerca de um
segundo. O pulso tem debounce de 1,2 s porque `SCREEN_ON` e `USER_PRESENT` normalmente
chegam juntos.

### 9. Padrões por dispositivo

**O que faz:** cria perfis coerentes em uma instalação nova.

**Como funciona:**

- lê as taxas do painel da lista estática da Samsung ou do fallback
  `Display.supportedModes` no Xiaomi;
- o perfil normal padrão é **máximo do painel → 60 Hz → mínimo do painel**, com esperas
  de 2 s e 5 s (no S25+ testado: 120 → 60 → 10);
- o ECO padrão começa em 60 Hz, segura por 2 s e depois usa o mínimo do painel;
- taxas não suportadas são rejeitadas no editor.

### 10. Configuração guiada

- verificação de compatibilidade do fabricante/tela;
- status e permissão do Shizuku;
- isenção opcional de otimização de bateria;
- whitelist opcional do Doze (pelo Shizuku);
- permissão de notificação;
- notificação persistente do serviço em primeiro plano.

### 11. Sem WakeLock

O serviço em primeiro plano opcional mantém o motor vivo sem segurar WakeLock. O aparelho
continua entrando no Doze. Mudanças de tela são tratadas por broadcasts do sistema, sem
polling.

### 12. Diagnóstico

- log em memória (até 500 entradas), não enviado e não persistido;
- filtros por tag e texto;
- estado do motor, Hz aplicado/observado, última escrita, detalhe de vídeo, estado do
  monitor de toque;
- botões de teste para a primeira/última faixa e ciclo completo;
- ações de segurança “Verificar controle da tela” e “Restaurar brilho do sistema”;
- relatório de diagnóstico pode ser copiado manualmente para um relato de bug.

### 13. Aparência

Temas sistema, claro, escuro e AMOLED. O AMOLED usa preto puro em painéis OLED.

### 14. Idiomas

Português (Brasil), inglês e espanhol. Veja o [status das traduções](#traduções) abaixo.

---

## Como funciona (resumo técnico)

1. O app conecta um **UserService do Shizuku** (`IOneHzService`) rodando no processo
   `onehz_core` com privilégios de shell/root.
2. O serviço lê eventos reais de toque com `getevent -lt` e notifica o app por um
   callback AIDL one-way. O `MtTouchParser` entende MT-B (tracking ids e slots).
3. O `OneHzEngine` roda na própria thread, mantém a escada e agenda um timer por faixa.
4. O `PolicyResolver` escolhe a situação: **tela desligada → AOD**, **economia → ECO**,
   caso contrário **normal**, com o perfil do app quando configurado.
5. O `SessionArbiter` grava:
   - `settings put secure refresh_rate_mode 2`;
   - `settings put system peak_refresh_rate <hz>`;
   - `settings put system min_refresh_rate <hz>`.
   As escritas são deduplicadas por época, então reaplicar o mesmo valor custa zero
   chamadas privilegiadas.
6. Ao liberar, o OneHZ volta ao modo adaptativo e apaga peak/min para o sistema retornar
   à faixa adaptativa original.
7. Se o Shizuku morre, o app falha fechado: para de controlar e mostra um aviso.
8. O UserService encerra junto com o app por um death recipient do binder, então não fica
   processo órfão após atualizações ou morte do processo.

Veja [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) para detalhes.

---

## Instalação

1. Instale o Shizuku (o fork testado está linkado acima).
2. Inicie o Shizuku via depuração sem fio ou root.
3. Instale o APK do OneHZ pelo [GitHub Releases](../../releases).
4. Abra o OneHZ e permita o acesso ao Shizuku.
5. Siga a tela de configuração: compatibilidade, Shizuku, permissões opcionais de
   bateria/Doze/notificação.
6. Ative a chave principal.

> Se você vem de uma versão debug assinada com outra chave, desinstale primeiro:
> `adb uninstall io.github.nevesz.onehz`.

### Atualizações automáticas com o Obtainium

O [Obtainium](https://obtainium.imranr.dev/) pode acompanhar os releases do OneHZ e avisar
quando houver uma versão nova:

1. Instale o Obtainium.
2. Adicione `https://github.com/neveSZ/onehz` como fonte, ou use o badge no topo.
3. Deixe o Obtainium verificar e instalar as novas versões.

O GitHub Releases e o Obtainium usam o mesmo APK assinado e a mesma chave. A checagem
opcional dentro do app é separada: ela só abre a página do release no GitHub quando você
ativa. O Obtainium é a forma recomendada de receber atualizações automáticas.

## Atualização

Instale o novo APK por cima do antigo. Atualizações exigem a mesma chave de assinatura.
Suas configurações são mantidas. Se o Shizuku estiver rodando, o app reconecta sozinho.

O OneHZ pode checar opcionalmente se há uma versão nova no GitHub quando você abre o
app. A checagem fica **desligada por padrão**: ative em **Ajustes → Atualizações**, aceite
a explicação e escolha o intervalo (24 horas, 3 dias, 7 dias ou 30 dias) e o canal
(somente estáveis ou estáveis + pré-releases). Ela nunca roda em segundo plano.

Para atualizações automáticas, use o Obtainium (veja a seção de instalação acima). A
checagem interna apenas avisa sobre um release; ela nunca baixa nem instala um APK.

## Desinstalação

1. Desative o brilho do AOD e use “Restaurar brilho do sistema”.
2. Desinstale o app.
3. Se a chave do AOD permanecer:
   `adb shell settings delete system debug_aod_brightness`.

---

## Privacidade e permissões

O OneHZ pede a permissão `INTERNET` **somente para a checagem opcional de atualizações**,
que fica desligada por padrão. Não há analytics, propaganda, telemetria nem conta. A
configuração fica local em `SharedPreferences`; o diagnóstico fica apenas em memória.

| Permissão | Motivo |
|---|---|
| `QUERY_ALL_PACKAGES` | Listar apps instalados para os perfis por aplicativo. |
| `RECEIVE_BOOT_COMPLETED` | Reaplicar o perfil após reiniciar. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Manter o serviço opcional do motor vivo. |
| `POST_NOTIFICATIONS` | Mostrar a notificação persistente do serviço. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Isenção opcional solicitada por você. |
| `INTERNET` | Checagem opcional de atualizações no GitHub, desligada por padrão. |
| Permissão do Shizuku | Executar as operações privilegiadas de tela/settings. |

O app **não** usa AccessibilityService, overlay, armazenamento, contatos, localização,
câmera ou microfone. A rede é usada somente quando você ativa a checagem de atualizações.

A checagem consulta a API pública do GitHub apenas quando você abre o OneHZ, respeita o
intervalo escolhido e nunca roda em segundo plano. Nenhum token, conta ou identificador é
enviado. Veja [PRIVACY.md](PRIVACY.md).

---

## Limitações conhecidas

- **Somente Samsung, Xiaomi, Redmi e POCO.** Outras marcas são rejeitadas pela
  verificação de compatibilidade.
- **Tela multi-taxa obrigatória.** A verificação exige pelo menos duas taxas; LTPO não
  é obrigatório.
- **Dois aparelhos testados.** Samsung S25+ `SM-S936B` (Android 17 / One UI 9.0) e POCO
  `2412DPC0AG` (Android 16 / HyperOS 3.0).
- Outras versões de Android/One UI e WQHD+ não foram testadas.
- O Shizuku precisa estar rodando. Após reiniciar, pode ser necessário iniciá-lo de novo
  via depuração sem fio/ADB.
- A detecção do display depende de settings do firmware e da saída do `dumpsys`; uma
  atualização de firmware pode mudar isso. O HyperOS usa `miui_refresh_rate` (ou
  `user_refresh_rate` no HyperOS 1).
- O brilho do AOD é **exclusivo da Samsung** (`debug_aod_brightness`); no Xiaomi a
  opção fica oculta.
- A detecção de vídeo depende do comportamento de MediaSession, Codec2 e AudioService; as
  regras e uma lista de apps forçados podem ser ajustadas no app, mas os sinais de base
  precisam existir.
- Vídeo em PiP ainda não é detectado.
- Se o Android matar o app enquanto uma taxa não padrão estiver aplicada (por exemplo, por
  gerenciamento agressivo de bateria), a última taxa pode permanecer até o OneHZ abrir de
  novo. Conceda a isenção de bateria opcional ou reabra o app para restaurar o padrão.
- Não use outro controlador de taxa de atualização ao mesmo tempo.
- Sem garantia. Este é um projeto pessoal.

## Backlog

- suporte a PiP na detecção de vídeo;
- mais modelos Samsung/Xiaomi (precisa de teste em aparelho real);
- brilho do AOD no Xiaomi (não foi encontrada rota via Settings; exigiria outro
  caminho privilegiado).

Doações são opcionais. Apoiadores fora do Brasil podem usar o
[GitHub Sponsors](https://github.com/sponsors/neveSZ); apoiadores no Brasil podem usar o
[LivePix](https://livepix.gg/onehz) (Pix/BRL). Elas **nunca** desbloqueiam funcionalidades.

---

## Compilação

Requisitos: **JDK 25**, Android SDK **37**, build-tools **36.0.0** e o Gradle wrapper
incluído no repositório.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Build release com R8 + redução de recursos:

```powershell
.\gradlew.bat assembleRelease
```

A assinatura é lida de `keystore.properties` (nunca versionado), de
`%USERPROFILE%\keys\onehz\keystore.properties` ou das variáveis de ambiente `ONEHZ_*`.
Veja [docs/BUILDING.md](docs/BUILDING.md) e
[keystore.properties.example](keystore.properties.example).

Tamanho atual do release: cerca de **3,2 MB**.

## Documentação

- [Arquitetura](docs/ARCHITECTURE.md)
- [Compilação](docs/BUILDING.md)
- [Testes](docs/TESTING.md)
- [Lançamento](docs/RELEASING.md)
- [Compatibilidade](docs/COMPATIBILITY.md)
- [FAQ](docs/FAQ.md)
- [Backlog](docs/BACKLOG.md)
- [Traduções](docs/TRANSLATIONS.md)
- [Privacidade](PRIVACY.md)
- [Segurança](SECURITY.md)

## Verificar o APK

Cada release inclui um arquivo `.sha256`. Você também pode inspecionar o assinante:

```powershell
apksigner verify --print-certs OneHZ-1.0.1.apk
```

SHA-256 do certificado:
`D8:E8:24:B2:5F:6B:1D:29:75:BD:72:D6:89:5F:C4:7F:7B:EB:C2:05:78:41:F7:83:3C:25:67:0A:C2:04:F8:0A`

---

## Apoio

Todas as funcionalidades são gratuitas e de código aberto. Doações são opcionais e nunca
desbloqueiam nada.

- **Fora do Brasil:** [GitHub Sponsors](https://github.com/sponsors/neveSZ) — mensal ou
  única.
- **Brasil:** [LivePix](https://livepix.gg/onehz) — Pix em BRL.

Escolha a opção que funciona melhor para você. Os dois links também estão no app em
**Sobre → Apoiar o OneHZ**.

Relatos de bugs e sugestões são bem-vindos pelo GitHub Issues. Inclua modelo do
aparelho, build do Android/One UI, versão do Shizuku, versão do OneHZ e o relatório de
diagnóstico. Remova dados pessoais antes de publicar.

## Licença e créditos

O OneHZ é distribuído sob a **Licença MIT**. Veja [LICENSE](LICENSE) e a
[tradução não oficial em português](LICENSE.pt-BR.md).

Créditos:

- **Autor:** [neveSZ](https://github.com/neveSZ).
- **Auxílio de LLM:** desenvolvido com ajuda de LLMs pelo harness **OpenCode**, depois
  revisado e testado pelo autor.
- **Adaptive Hz**, de Mahmut Alperen Ünal (MIT): o OneHZ inclui porções derivadas dele.
  Veja [NOTICE](NOTICE) e [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Shizuku**, de RikkaApps (Apache 2.0) e o **fork do thedjchi** (Apache 2.0).
- **AndroidX / Jetpack Compose / Kotlin / kotlinx.coroutines** (Apache 2.0).
- **JUnit 4** (somente testes, Eclipse Public License 1.0; não distribuído no app).

Nenhum código ou asset de aplicativos closed-source está incluído.

### Traduções

- **Português (Brasil):** revisado pelo autor.
- **Inglês:** gerado por IA, **não revisado por falante nativo**.
- **Espanhol:** gerado por IA, revisado para consistência, **não revisado por falante
  nativo**.

Correções são bem-vindas. Veja [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md).

## Aviso legal

O OneHZ não é afiliado, endossado ou patrocinado pela Samsung Electronics. Samsung,
Galaxy e One UI são marcas da Samsung Electronics. Android é uma marca da Google LLC.
Use o OneHZ por sua conta e risco; o software é fornecido “como está”, sem garantia de
qualquer tipo.
