# Instalação e atualização

[English](INSTALL.md) · **Português (Brasil)**

O OneHZ é distribuído como APK assinado no GitHub Releases. Ele não está no Google Play
e depende do Shizuku.

## Requisitos

- Celular Samsung **ou** Xiaomi/Redmi/POCO (HyperOS/MIUI) cuja tela exponha pelo menos
  duas taxas de atualização.
- Android 12 (API 31) ou superior.
- Shizuku instalado e em execução.
- A mesma chave de assinatura em toda atualização.

Aparelhos suportados e a verificação de compatibilidade:
[COMPATIBILIDADE](COMPATIBILITY.pt-BR.md).

## Shizuku

O OneHZ foi testado com o **fork do thedjchi `v13.7.0-thedjchi`**:
<https://github.com/thedjchi/Shizuku/tree/v13.7.0-thedjchi>

O Shizuku oficial deve funcionar porque o OneHZ usa a API padrão, mas não foi testado. Se
quiser reproduzir o ambiente testado, use a mesma versão do fork.

Inicie o Shizuku pela depuração sem fio ou root. Depois de reiniciar, ele pode precisar
ser iniciado de novo; o OneHZ reconecta quando ele ficar disponível e falha fechado até
lá.

O OneHZ **não** usa `AccessibilityService`. Serviços de acessibilidade podem interferir
em apps bancários e outros aplicativos sensíveis; todo o acesso privilegiado passa pelo
Shizuku.

## Instalação

1. Instale o Shizuku e inicie-o.
2. Baixe o APK no [GitHub Releases](../../releases).
3. Abra o APK e instale. O Android pode pedir para permitir instalações dessa origem.
4. Abra o OneHZ e permita o acesso ao Shizuku.
5. Siga a tela de configuração: compatibilidade, Shizuku, permissões opcionais de
   bateria/Doze/notificação.
6. Ative a chave principal.

> Se você vem de uma versão debug assinada com outra chave, desinstale primeiro:
> `adb uninstall io.github.nevesz.onehz`.

## Builds beta

Builds beta são publicadas como **pré-releases** na mesma página de releases e usam o
mesmo nome de pacote e a mesma chave.

- Estável e beta não podem ser instaladas lado a lado.
- O `versionCode` da beta é maior que o da estável atual, então ela instala por cima da
  1.0.1 mantendo os ajustes.
- A futura versão estável tem `versionCode` maior que o da beta, então quem está na beta
  volta ao estável instalando por cima.
- Na verificação interna de atualizações, escolha **estável + pré-releases** para ser
  avisado sobre betas. O canal estável nunca oferece uma pré-release.
- O Obtainium também acompanha pré-releases; ative essa opção nas configurações da fonte.

## Atualizações automáticas com o Obtainium

O [Obtainium](https://obtainium.imranr.dev/) pode acompanhar os releases do OneHZ e avisar
quando houver uma versão nova:

1. Instale o Obtainium.
2. Adicione `https://github.com/neveSZ/onehz` como fonte, ou use o badge no README.
3. Deixe o Obtainium verificar e instalar as novas versões.

O GitHub Releases e o Obtainium usam o mesmo APK assinado e a mesma chave. A verificação
opcional dentro do app é separada: ela só abre a página do release no GitHub quando você
ativa. O Obtainium é a forma recomendada de receber atualizações automáticas.

## Atualização

Instale o novo APK por cima do antigo. Atualizações exigem a mesma chave de assinatura.
Seus ajustes e perfis são mantidos. Se o Shizuku estiver rodando, o app reconecta
sozinho.

O OneHZ pode checar opcionalmente se há uma versão nova no GitHub quando você abre o
app. A checagem fica **desligada por padrão**: ative em **Ajustes → Atualizações**,
aceite a explicação e escolha o intervalo (24 horas, 3 dias, 7 dias ou 30 dias) e o canal
(somente estáveis ou estáveis + pré-releases). Ela nunca roda em segundo plano.

A verificação interna apenas avisa sobre um release; ela nunca baixa nem instala um APK.

## Desinstalação

1. Desative o brilho do AOD e use “Restaurar brilho do sistema” (Samsung).
2. Desinstale o app.
3. Se a chave do AOD permanecer:
   `adb shell settings delete system debug_aod_brightness`.

## Verificar o APK

Cada release inclui um arquivo `.sha256` ao lado do APK.

```powershell
Get-FileHash .\OneHZ-<versão>.apk -Algorithm SHA256
apksigner verify --print-certs .\OneHZ-<versão>.apk
```

O SHA-256 do certificado de assinatura é:

`D8:E8:24:B2:5F:6B:1D:29:75:BD:72:D6:89:5F:C4:7F:7B:EB:C2:05:78:41:F7:83:3C:25:67:0A:C2:04:F8:0A`

Se a impressão digital não conferir, não instale o APK. Instale apenas builds da página
oficial de releases do GitHub.

## Comandos de recuperação

Redefinir as chaves de taxa da Samsung para o padrão do sistema:

```powershell
adb shell settings put secure refresh_rate_mode 2
adb shell settings delete system peak_refresh_rate
adb shell settings delete system min_refresh_rate
```

Inspecionar as chaves do HyperOS (Xiaomi/Redmi/POCO):

```powershell
adb shell settings get secure miui_refresh_rate
adb shell settings get secure user_refresh_rate
```

O OneHZ salva e restaura essas chaves ao liberar o controle; os comandos acima servem
apenas para recuperação manual depois que o app foi encerrado ou desinstalado em um
momento incomum.

Outras dúvidas estão nas [perguntas frequentes](FAQ.pt-BR.md).
