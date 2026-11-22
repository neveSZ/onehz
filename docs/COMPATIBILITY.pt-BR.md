# Compatibilidade

[English](COMPATIBILITY.md) · **Português (Brasil)**

## Configurações testadas

| Item | Samsung Galaxy S25+ | POCO X7 Pro |
|---|---|---|
| Aparelho | `SM-S936B` (`pa2q`) | `2412DPC0AG` (`rodin`) |
| Android | 17 (API 37) | 16 (API 36) |
| Software | One UI 9.0 (`ro.build.version.oneui=90000`) | HyperOS 3.0 (`OS3.0.304.0.WOJMIXM`) |
| Build | `S936BXXUDDZJB` | `BP2A.250605.031.A3` |
| Patch de segurança | 2026-10-05 | 2026-10-01 |
| Resolução testada | 1080 × 2340 (FHD+) | 1220 × 2712 |
| Taxas do painel | 120, 80, 60, 48, 40, 30, 26,67, 24, 20, 10 Hz | 120, 90, 60, 30 Hz |
| Rota de controle | `refresh_rate_mode` + `peak/min` | `secure/miui_refresh_rate` |
| Shizuku | fork thedjchi `v13.7.0-thedjchi` | fork thedjchi `v13.7.0-thedjchi` |
| OneHZ | 1.2.1 | 1.2.1 |

Todo o resto é **não testado**.

## Verificação de compatibilidade

`DisplayGate.evaluate()` aceita um aparelho somente quando todas as verificações passam:

1. o fabricante/marca é **Samsung** ou **Xiaomi/Redmi/POCO**;
2. a rota do fabricante existe:
   - Samsung: `Settings.Secure.refresh_rate_mode`;
   - Xiaomi: `Settings.Secure.miui_refresh_rate` ou `user_refresh_rate`;
3. a tela expõe **pelo menos duas taxas de atualização distintas**.

**LTPO não é obrigatório.** Uma tela 120/90/60/30 é aceita. Se qualquer verificação
falhar, o OneHZ mostra “Tela não suportada” com o motivo e não grava nada.

Verifique o aparelho localmente:

```powershell
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.product.brand
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.oneui
adb shell getprop ro.mi.os.version.name
adb shell dumpsys display | Select-String "mSupportedRefreshRates"
adb shell settings get secure refresh_rate_mode
adb shell settings get secure miui_refresh_rate
adb shell settings get secure user_refresh_rate
```

## Aparelhos suportados

- Samsung com pelo menos duas taxas de atualização.
- Xiaomi/Redmi/POCO com HyperOS/MIUI e pelo menos duas taxas de atualização.
- Testado somente no S25+ e no POCO X7 Pro acima.

## Limitações do painel

### Brilho em 30 Hz no HyperOS

No POCO X7 Pro testado (`2412DPC0AG`), 30 Hz é visivelmente mais escuro que 60/120 Hz no
mesmo brilho de sistema. A diferença é gerada pelo painel/PQ do fabricante quando o modo
de tela muda, não pelo Android:

- `screen_brightness` e `debug.tracing.screen_brightness` são idênticos em 30 e 120 Hz;
- o framebuffer é idêntico (mesmo conteúdo, mesmos pixels);
- o backlight informado é idêntico;
- o modo de dimming do fabricante (`DisplayFeatureManager` `setScreenEffect`) e
  `cmd display set-brightness` não mudam isso;
- o Shizuku roda como `shell` (UID 2000, sem root), e as rotas de sysfs/propriedades do
  fabricante que poderiam mudar a gama do painel são negadas pelo SELinux.

Por isso o OneHZ não consegue igualar o brilho. Manter 30 Hz é suportado, e o editor de
perfis mostra um aviso em aparelhos Xiaomi quando uma faixa usa 30 Hz. No brilho máximo
não há margem para compensar para cima, e escurecer 60/120 Hz para igualar 30 Hz só
deixaria a tela inteira mais escura.

## Limitações conhecidas

- **Somente Samsung, Xiaomi, Redmi e POCO.** Outros fabricantes são rejeitados pela
  verificação de compatibilidade.
- **Tela multi-taxa obrigatória.** A verificação exige pelo menos duas taxas de
  atualização; LTPO não é obrigatório.
- **Telas com duas taxas.** Economia, Equilibrado e Desempenho usam as mesmas duas faixas
  e diferem apenas no tempo na máxima. O tempo na mínima é o repouso até o próximo toque.
- **Mínimo do AOD.** O AOD usa a menor taxa exposta pela tela. Em painéis que rejeitam
  taxas muito baixas no AOD, o motor cai para 30 Hz e registra a falha.
- **Dois aparelhos testados.** Samsung S25+ `SM-S936B` (Android 17 / One UI 9.0) e
  POCO X7 Pro `2412DPC0AG` (Android 16 / HyperOS 3.0).
- Outras versões de Android/One UI/HyperOS e WQHD+ não foram testadas.
- O Shizuku precisa estar rodando. Após reiniciar, pode ser necessário iniciá-lo de novo
  via depuração sem fio/ADB.
- A detecção da tela depende de ajustes do firmware e da saída do `dumpsys`; uma
  atualização de firmware pode mudar isso. O HyperOS usa `miui_refresh_rate` (ou
  `user_refresh_rate` no HyperOS 1).
- O brilho do AOD é **somente Samsung** (`debug_aod_brightness`). No Xiaomi a linha
  aparece como um switch desabilitado explicando que o painel cuida disso.
- **O Sono (Doze forçado) depende do fabricante.** Algumas ROMs ignoram ou revertem o
  idle forçado; o card mostra “A ROM não entrou em Doze” em vez de fingir que funcionou.
- **Light idle no HyperOS:** no POCO X7 Pro testado, o deep idle é forçado
  (`mForceIdle=true`), enquanto o light idle já é o estado normal com a tela apagada
  (`mState=IDLE`, `mLightState=OVERRIDE`) e não altera `mForceIdle`.
- **Entrega do SCREEN_OFF no HyperOS:** no mesmo aparelho, o broadcast `SCREEN_OFF` às
  vezes chegou cerca de 10 segundos depois do keyevent com o app em segundo plano
  (`QUICK_DOZE_DELAY`); o serviço em primeiro plano ainda o recebeu e o force foi
  aplicado em seguida.
- **As medições do Sono incluem o AOD.** Em aparelhos com Always On Display, a janela de
  tela apagada também contém o consumo do AOD, então o %/h não é um número de deep idle
  puro.
- A detecção de vídeo depende do comportamento de MediaSession, Codec2 e AudioService; as
  regras e a lista de apps forçados podem ser ajustadas no app, mas os sinais de base
  precisam existir.
- Vídeo em PiP ainda não é detectado.
- Se o Android matar o app enquanto uma taxa não padrão estiver aplicada (por exemplo, por
  gerenciamento agressivo de bateria), a última taxa pode permanecer até o OneHZ abrir de
  novo. Conceda a isenção opcional de bateria ou reabra o app para restaurar o padrão.
- Não use outro controlador de taxa de atualização ao mesmo tempo.
- Sem garantia. Este é um projeto pessoal.

## Não suportado / não testado

- outros fabricantes (bloqueados pela verificação);
- aparelhos com uma única taxa de atualização (bloqueados);
- Samsung sem `refresh_rate_mode` (bloqueado);
- Xiaomi sem `miui_refresh_rate`/`user_refresh_rate` (bloqueado);
- brilho do AOD no Xiaomi (somente Samsung; aparece desabilitado com explicação);
- dobráveis e telas de capa (não testados);
- tablets (não testados);
- modo WQHD+ no Samsung testado (não testado);
- Android 12–15 (o minSdk permite, mas somente Android 16 e 17 foram testados);
- HyperOS 1/2 (o fallback de rota existe, mas somente o HyperOS 3 foi testado);
- outras versões do Shizuku.

## Dependências de firmware

Samsung:

- `refresh_rate_mode`;
- `peak_refresh_rate`, `min_refresh_rate`;
- `debug_aod_brightness`;
- chaves de taxa da economia de bateria da Samsung (`pms_*`/`psm_*`).

Xiaomi/HyperOS:

- `miui_refresh_rate` (HyperOS 2/3);
- `user_refresh_rate` (fallback do HyperOS 1).

Uma atualização de firmware pode renomear, restringir ou remover esses ajustes. Nesse
caso, o OneHZ falha fechado e mostra o estado detectado. Relate o build do firmware e o
relatório de diagnóstico.

## Shizuku

- Testado com o **fork do thedjchi `v13.7.0-thedjchi`** no S25+ e no POCO X7 Pro:
  <https://github.com/thedjchi/Shizuku/tree/v13.7.0-thedjchi>
- O [Shizuku](https://github.com/RikkaApps/Shizuku) oficial deve funcionar porque o app
  usa a API padrão, mas não foi testado.
- Após reiniciar, o Shizuku pode precisar ser iniciado de novo por depuração sem fio ou
  ADB. O OneHZ reconecta quando ele fica disponível.

## Relatar compatibilidade

Inclua:

- modelo, produto, Android, One UI/HyperOS, build, patch de segurança;
- taxas do painel obtidas com `dumpsys display`;
- valores das rotas (`refresh_rate_mode`, `miui_refresh_rate`, `user_refresh_rate`);
- versão do Shizuku;
- se a verificação mostra OK;
- o que funciona e o que não funciona.

Não publique dados pessoais.
