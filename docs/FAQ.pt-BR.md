# Perguntas frequentes

[English](FAQ.md) · **Português (Brasil)**

### O OneHZ é gratuito?

Sim. Todas as funcionalidades são gratuitas e de código aberto (MIT). Doações pelo
<https://livepix.gg/onehz> são opcionais e nunca desbloqueiam nada.

### O OneHZ precisa de root?

Não. Ele usa o **Shizuku**, que pode rodar por depuração sem fio ou root. Root não é
necessário.

### Por que Shizuku em vez de acessibilidade?

O AccessibilityService pode interferir em apps bancários e outros aplicativos sensíveis.
O OneHZ evita isso de propósito. O Shizuku oferece um canal privilegiado mais estreito e
explícito.

### Por que somente Samsung e Xiaomi/Redmi/POCO?

O controle da tela usa ajustes específicos de cada fabricante: `refresh_rate_mode` e
`peak/min` na Samsung, e `miui_refresh_rate` (ou `user_refresh_rate`) no HyperOS. Outros
fabricantes usam rotas diferentes, então o app os bloqueia em vez de fingir que
funciona.

### Por que a tela precisa de duas taxas de atualização?

A escada alterna entre taxas. Uma tela de taxa única não tem para onde mudar, então o app
falha fechado. **LTPO não é obrigatório**; uma tela 120/90/60/30 já basta.

### O brilho do AOD funciona no Xiaomi?

Não. O brilho do AOD do Xiaomi é automático e controlado pelo processo/sensor de luz do
AOD (`aod_brightness_level` é um recurso de fábrica, não uma chave de Settings). A rota
`debug_aod_brightness` da Samsung não existe lá, então a opção fica oculta no Xiaomi.

### Por que a tela fica mais escura em 30 Hz no Xiaomi?

No POCO X7 Pro testado, o próprio modo de 30 Hz do painel é mais escuro: brilho do
sistema, framebuffer e backlight informado são idênticos aos de 60/120 Hz, mas o
painel/PQ do fabricante aplica uma gama diferente. Os controles do fabricante acessíveis
pelo Shizuku (`shell`, sem root) não mudam isso, então nenhum app consegue compensar. O
OneHZ mantém 30 Hz disponível e mostra um aviso no editor de perfis. Veja
[COMPATIBILIDADE](COMPATIBILITY.pt-BR.md#brilho-em-30-hz-no-hyperos).

### Qual aparelho foi testado?

Um Samsung Galaxy S25+ (`SM-S936B`), Android 17, One UI 9.0, build `S936BXXUDDZJB`, e um
POCO X7 Pro (`2412DPC0AG`, `rodin`), Android 16, HyperOS 3.0 (`OS3.0.304.0.WOJMIXM`).
Veja [COMPATIBILIDADE](COMPATIBILITY.pt-BR.md).

### Como obter builds beta?

Builds beta são publicadas como pré-releases na mesma
[página de releases](https://github.com/neveSZ/onehz/releases):

- instale o APK manualmente a partir da pré-release, ou
- ative **estável + pré-releases** na verificação interna de atualizações, ou
- ative pré-releases para a fonte do OneHZ no Obtainium.

Uma beta usa a mesma chave de assinatura e um `versionCode` maior que o da estável atual,
então ela instala por cima da 1.0.1 mantendo os ajustes. A próxima estável tem
`versionCode` maior, então instalar a estável por cima da beta devolve você ao canal
estável.

### O que mudou na 1.1.0?

- Suporte a Xiaomi/Redmi/POCO (HyperOS 2/3, com fallback para HyperOS 1).
- O requisito de LTPO foi substituído por uma verificação de capacidade: pelo menos duas
  taxas de atualização.
- Novos perfis padrão em instalações novas: normal **máximo → 60 Hz → mínimo** (2 s /
  5 s) e ECO **60 Hz → mínimo** (2 s).
- Regras de detecção de vídeo configuráveis e lista de apps forçados.
- O diagnóstico agora inclui brilho/modo da tela, estado da economia de bateria e a rota
  de taxa ativa.

### Atualizar vai mudar meus perfis?

Não. Os perfis existentes são mantidos ao atualizar, inclusive da 1.0.1 para esta beta.
Os novos padrões só são criados em uma instalação nova.

### Por que o controle para depois de reiniciar?

O Shizuku pode precisar ser reiniciado após um reboot (depuração sem fio/ADB). O OneHZ
reconecta quando o Shizuku fica disponível e falha fechado até lá.

### Por que o OneHZ não está no Google Play?

Ele usa Shizuku, `QUERY_ALL_PACKAGES` e isenção de otimização de bateria, além de alterar
ajustes internos da Samsung e do HyperOS. A distribuição por GitHub Releases mantém o
projeto simples e transparente.

### O OneHZ coleta dados?

Nenhum dado pessoal é coletado. A verificação opcional de atualizações usa a internet
somente depois que você ativa; não há analytics, anúncios nem telemetria. Veja
[PRIVACIDADE](../PRIVACY.md).

### O brilho do AOD ficou preso depois de desinstalar. O que faço?

Execute:

```powershell
adb shell settings delete system debug_aod_brightness
```

ou reinstale o OneHZ e use **Sobre/Diagnóstico → Restaurar brilho do sistema**.

### Por que o perfil de economia se chama ECO?

“ECO” é uma sigla curta e neutra que evita confusão com nomes de plataforma/API. O texto
descritivo continua dizendo “economia de bateria” / “battery saver” / “ahorro de
batería”.

### Por que a taxa não muda em alguns apps?

Alguns apps/jogos definem a própria taxa preferida ou usam sobreposições de taxa na
superfície. O OneHZ controla os valores de pico/mínimo do sistema; ele não consegue
sobrepor toda decisão do app. Relate o app e o estado do diagnóstico.

### Funciona junto com outro controlador de taxa de atualização?

Não. Rodar dois controladores ao mesmo tempo causa escritas conflitantes. Desative o
outro.

### A taxa ficou fixa depois que o app foi encerrado. Como restauro?

Abra o OneHZ de novo; na próxima conexão com o Shizuku ele restaura a sessão pendente. Se
isso não funcionar, na Samsung execute:

```powershell
adb shell settings put secure refresh_rate_mode 2
adb shell settings delete system peak_refresh_rate
adb shell settings delete system min_refresh_rate
```

No Xiaomi/Redmi/POCO, confira as chaves com:

```powershell
adb shell settings get secure miui_refresh_rate
adb shell settings get secure user_refresh_rate
```

Conceda a isenção opcional de bateria para reduzir a chance de o app ser encerrado.

### Por que 1 Hz não aparece?

O OneHZ lista as taxas que o sistema expõe para o painel atual. No S25+ testado, o
sistema expõe 10 Hz como mínimo mesmo o painel sendo LTPO. O app não inventa taxas que o
firmware não informa.

### Posso compilar sozinho?

Sim. Veja [COMPILAÇÃO](BUILDING.md). Você precisa de JDK 25, SDK 37 e build-tools
36.0.0.

### Posso usar em outro modelo Samsung ou Xiaomi?

Sim, se ele tiver pelo menos duas taxas de atualização e a rota do fabricante existir.
Não foi testado em outros modelos. Relate modelo, build, taxas do painel e diagnóstico se
você testar.

### Por que inglês e espanhol são marcados como gerados por IA?

Porque são. O autor revisou o português. Inglês e espanhol foram gerados e revisados para
consistência, mas não por falantes nativos. Correções são bem-vindas.

### O Sono força o Doze? Vou perder notificações?

O Sono é opcional e vem desligado. Quando ativado, ele pede ao Android para entrar em
Doze assim que a tela apaga, então rede e trabalho em segundo plano esperam as janelas de
manutenção. Chamadas tradicionais e alarmes ainda acordam o aparelho, mas uma chamada VoIP
que depende de conexão em segundo plano pode não chegar até a próxima janela. Use
**Pausar por 1 hora**, a janela de horário ou adicione o app em **Apps que podem acordar o
aparelho** quando precisar dele em tempo real.
