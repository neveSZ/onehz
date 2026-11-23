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

### O que são Economia, Equilibrado e Desempenho?

São três perfis base gerados conforme a tela:

- **Economia** usa as faixas mais baixas úteis e é aplicado automaticamente enquanto a
  economia de bateria do sistema está ligada;
- **Equilibrado** é o perfil padrão e usa a maior taxa acima de 60 Hz quando existe;
- **Desempenho** mantém um repouso de 60 Hz ou mais.

Você pode escolher o perfil ativo na Home, editar qualquer perfil base e restaurá-lo para
o padrão do aparelho. Perfis personalizados também são suportados.

### Por que o card mostra um perfil selecionado e outro em uso?

Quando a **Economia automática** está ligada e a economia de bateria do sistema está
ativa, o OneHZ usa Economia mesmo que outro perfil esteja selecionado. O card mostra o
perfil selecionado e o perfil em uso. Desligue a **Economia automática** em **Gerenciar
perfis** para usar sempre o perfil selecionado.

### O que acontece em uma tela com apenas duas taxas?

Os três perfis usam as mesmas duas faixas e diferem apenas no tempo na máxima (1 s / 2 s /
5 s). A mínima é o repouso até o próximo toque, então há apenas uma espera ajustável
nesses painéis.

### Qual taxa o AOD usa?

A menor taxa exposta pela tela. Não existe mais seletor separado de frequência do AOD. Se
o painel rejeitar uma taxa muito baixa no AOD, o motor cai para 30 Hz e registra a falha.

### Posso fazer backup das configurações?

Sim. Ajustes → **Backup e restauração** exporta um JSON versionado pelo seletor de
arquivos do sistema. Ele inclui todos os perfis, atribuições de apps, vídeo, AOD,
configurações do Sono e o switch mestre. Tema, idioma, diagnóstico, medições do Sono e
exceções do Doze do sistema não entram. A importação substitui a configuração de
comportamento atual e adapta taxas não suportadas para a tela atual. Um backup criado
antes de o Sono existir **não** altera as configurações atuais do Sono.

### O que é o Sono (Doze)?

O Sono é um recurso opcional que pede ao Android para entrar em **Doze** assim que a tela
apaga. Doze é o estado de repouso profundo do Android. Ligações normais e alarmes
continuam funcionando, mas notificações e chamadas por internet (WhatsApp, Telegram etc.)
podem atrasar. O Sono vem desligado, pede confirmação antes do primeiro enable e pode ser
pausado por 1 hora a qualquer momento. Veja [SONO](SLEEP.md).

### O brilho do AOD funciona no Xiaomi?

Não. O brilho do AOD do Xiaomi é automático e controlado pelo processo/sensor de luz do
AOD (`aod_brightness_level` é um recurso de fábrica, não uma chave de Settings). A rota
`debug_aod_brightness` da Samsung não existe lá, então a linha de brilho aparece como um
switch desabilitado explicando que o painel cuida disso.

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
então ela instala por cima da versão estável mantendo os ajustes. A próxima estável tem
`versionCode` maior, então instalar a estável por cima da beta devolve você ao canal
estável.

### O que mudou na 1.2.1?

- O switch de taxa do AOD está disponível em todos os aparelhos suportados. Aparelhos que
  não são Samsung mostram a linha de brilho do AOD desativada, com explicação.
- O detalhe do Sono foi redesenhado: um único master na Home, opções de comportamento
  sempre disponíveis, um aviso dispensável, linha de status só para estados de
  ativo/pausado/falha e ícones consistentes nos switches.
- As opções de vídeo ficaram mais claras: a taxa mínima é separada das regras de detecção,
  e as regras + lista global de apps abrem em uma sheet dedicada.
- As preferências foram para o DataStore com migração automática. Exporte um backup antes
  de fazer downgrade, porque o XML antigo é limpo após a migração.
- Baseline Profile, ícones embutidos, edge-to-edge, ViewModels e ferramentas de release.
- Distribuição pelo Shizu Store (recomendado), GitHub Releases e Obtainium.

### O que mudou na 1.2.1-beta.1?

- O switch do AOD na Home agora é o único master; a tela de detalhe não repete a chave.
- Taxa e brilho do AOD voltaram a ser independentes. Desligar a taxa não esconde mais o
  brilho nem a ação de restauração.
- O master do AOD é migrado das escolhas antigas de taxa/brilho, e os backups existentes
  continuam compatíveis.
- Com o Sono desligado, o detalhe mantém comportamento, apps que acordam e medições
  visíveis e editáveis; testar e pausar exigem o Sono ligado.
- O subtítulo do Sono na Home agora mostra "Desligado" quando o recurso está desligado.

### O que mudou na 1.2.0?

- Perfis reutilizáveis **Economia, Equilibrado e Desempenho**, além de perfis
  personalizados.
- Seleção manual do perfil ativo, com **Economia automática** opcional durante a economia
  de bateria.
- Perfis por app agora referenciam um perfil salvo em vez de copiar a escada.
- Mínimo de vídeo e regras de detecção por perfil; a lista de apps forçados é global.
- O AOD sempre usa a mínima da tela; o brilho do AOD tem ação de restauração do sistema.
- **Sono** opcional (Doze forçado), com aviso antes do primeiro enable, proteções e
  medições locais.
- Backup inclui as configurações do Sono; um backup sem Sono não altera as atuais.
- Home reorganizada; AOD e Sono agora têm telas de detalhe.

### Atualizar vai mudar meus perfis?

As escadas são mantidas, mas o modelo muda. Normal vira Equilibrado, ECO vira Economia e
um novo perfil Desempenho é criado. Perfis por app copiados viram referências a um perfil
correspondente (ou a um perfil customizado deduplicado). As preferências são migradas para
o DataStore na primeira abertura; o XML antigo é limpo, então um downgrade depois da
migração mostra os padrões para ajustes alterados depois. Se quiser o layout antigo de
volta, edite os perfis ou restaure os base para o padrão do aparelho.

### Por que o controle para depois de reiniciar?

O Shizuku pode precisar ser reiniciado após um reboot (depuração sem fio/ADB). O OneHZ
reconecta quando o Shizuku fica disponível e falha fechado até lá.

### Por que o perfil do app não foi aplicado logo depois de abrir?

Versões antigas reliam o app em primeiro plano só no toque para baixo, então um app aberto
pela launcher, recentes ou notificação podia continuar sendo tratado como o app anterior
até você tocar dentro dele. O OneHZ agora acompanha mudanças de task-stack com a tela
ligada e, quando a plataforma rejeita esse listener, faz uma leitura no toque para cima.
Se ainda acontecer, toque uma vez dentro do app e envie um relatório de diagnóstico.

### Por que o OneHZ não está no Google Play?

Ele usa Shizuku, `QUERY_ALL_PACKAGES` e isenção de otimização de bateria, além de alterar
ajustes internos da Samsung e do HyperOS. A distribuição pelo Shizu Store, GitHub
Releases e Obtainium mantém o projeto simples e transparente.

### O OneHZ coleta dados?

Nenhum dado pessoal é coletado. A verificação opcional de atualizações usa a internet
somente depois que você ativa; não há analytics, anúncios nem telemetria. Veja
[PRIVACIDADE](../PRIVACY.md).

### Como abro o Diagnóstico?

Abra **Ajustes → Diagnóstico**. O relatório é gerado localmente, pode ser pausado ou
limpo nessa tela e só é compartilhado quando você copia.

### O brilho do AOD ficou preso depois de desinstalar. O que faço?

Execute:

```powershell
adb shell settings delete system debug_aod_brightness
```

ou reinstale o OneHZ e use **Ajustes → Diagnóstico → Restaurar brilho do sistema**.

### Por que o perfil de economia se chama Economia?

“Economia” é o nome visível para o perfil de faixas mais baixas. “ECO” permanece apenas em
entradas antigas do changelog e em chaves internas de compatibilidade; não é mais o nome
do perfil.

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

### O Sono pausa quando tem música tocando?

Por padrão, sim: a proteção de música pausa o Doze enquanto há áudio tocando. Ligue
**Doze com música** no detalhe do Sono para manter o Doze mesmo durante a reprodução.
Alguns aparelhos podem interromper streaming ou áudio em segundo plano no Doze profundo,
por isso a opção vem desligada. A condição é reavaliada quando a música começa ou termina e
quando o carregador é conectado ou removido; não é preciso apagar a tela de novo.

### O Sono mantém o Doze durante o carregamento?

Por padrão, sim. **Doze durante o carregamento** fica ligado, então o carregador não pausa
o Sono. Desligue no detalhe do Sono para pausar o Doze enquanto o aparelho está na tomada.
O estado do carregador é reavaliado quando o cabo é conectado ou removido.

### Como as medições do Sono funcionam?

Tudo é local. “Últimas 24h” é uma janela móvel: o tempo com a tela apagada e o tempo em
Doze são as somas dos trechos registrados recortados nessa janela. O “último descanso” é
o período contínuo mais recente de tela apagada com pelo menos 30 minutos; intervalos com
a tela ligada menores que dois minutos são mesclados. A taxa de bateria é a queda de nível
da sessão por hora e aparece como `—` para sessões menores que 15 minutos ou quando o
nível aumentou (carregando). Os números incluem o AOD e são medidos pelo OneHZ, não pelo
sistema; nada é enviado. Se o app não estava rodando a janela inteira, os totais aparecem
com o prefixo “parcial”.
