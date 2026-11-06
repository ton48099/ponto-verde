# Ponto Verde 0.2.2

## Sobre
Avaliador local de ofertas de corrida para Android. Lê a oferta que aparece na tela dos apps de motorista (Uber, 99, inDrive, iFood, Taxi Machine, Regional), calcula R$/km e R$/h, pinta um cartão com a cor da oferta e guarda tudo num histórico local, dia a dia.

## Instalação
Baixe o APK abaixo e instale no seu Android (requer Android 11+).

## Permissões necessárias
- **Acessibilidade:** para ler ofertas dos apps de motorista
- **Sobrepor janelas:** para mostrar o cartão flutuante sobre a tela
- **Notificações:** para o vigia do copiloto (opcional)
- **Internet:** só usada quando o corretor IA está ligado; desligado por padrão

## Correções na versão 0.2.2

- **O cartão parou de piscar quando o card da Uber pisca.** O card da oferta na Uber se redesenha a cada ~0,4 s; cada redesenho gerava um evento e o leitor escondia o nosso cartão para tirar o print, mostrava de novo, e assim por diante — na prática o cartão configurado para 5 s durava menos de 1 s. Agora:
  - enquanto o nosso cartão está na tela, o leitor **não captura** (nada de esconder/reescrever);
  - a **mesma oferta** lida de novo em até 15 s não abre outro cartão nem grava outra linha no histórico/log (log: `mesma oferta de agora há pouco; cartão e histórico mantidos`).
  - Medido no aparelho: cartão na tela por ~5 s, sem piscar; oferta diferente abre cartão novo normalmente.
- **Texto de permissão mais honesto.** "Acessibilidade ligada, mas o serviço está parado" virou "Acessibilidade ligada; o serviço está religando (toque para abrir as configurações)". O Android religa o serviço sozinho no primeiro evento de outro app; o texto antigo assustava sem motivo. A linha agora é clicável e leva direto ao ajuste do sistema.
- `LogTreino` passa a gravar a versão do app a partir do `BuildConfig.VERSION_NAME` (antes ficava fixa no código).

## Correções na versão 0.2.1

- **Modelo padrão do corretor IA trocado para `nvidia/nemotron-3.5-bolt-e4b`.** O padrão anterior (`nemotron-3-ultra-550b-a55b`) devolvia conteúdo vazio com `max_tokens=300`, então o botão **Testar conexão** falhava mesmo com a chave certa.
- Escolha feita por benchmark com 11 cartões reais e sintéticos: o **bolt** acertou todos (incluindo `lhe55 min` → 55 min, milhar rejeitado, `Est. de R$/km` ignorado, surge e não-oferta) em 0,45–2,0 s.
- `nemotron-3-super-120b-a12b` devolve 404 nessa conta; `deepseek-v4.1-flash`, `gpt-oss-20b` e `glm-5.3-flash` estouram 60 s — por isso não são o padrão.

## Novidades na versão 0.2.0

### Log de treino
- **Registrar cada leitura** (Ajustes → Avançado → Opções de desenvolvedor) grava, para cada oferta lida:
  - a imagem recortada que o OCR enxergou (`.jpg`);
  - o texto bruto do ML Kit, o que o parser entendeu, a avaliação final e a resposta da IA (`.json`).
- **Exportar ZIP para Downloads** junta tudo em `Download/Ponto Verde/`.
- **Compartilhar ZIP** manda por WhatsApp, Drive ou e-mail.
- Serve para calibrar o parser com ofertas reais: quando uma leitura erra, dá para ver exatamente o que a tela mostrava e o que o OCR devolveu.

### Corretor IA (opcional, NVIDIA Nemotron)
- Quando o parser local não entende a oferta (ou em toda leitura, se ligado), manda o OCR bruto para a API da NVIDIA e usa o JSON devolvido.
- Chave, modelo e endpoint configuráveis; padrão `nvidia/nemotron-3.5-bolt-e4b`.
- Botões **Testar conexão** e **Listar modelos da conta**.
- Desligado por padrão. Sem API key, o app não abre conexão.

### Leitura e copiloto
- **Bolha PV = leitura forçada:** tocar na bolha captura a tela na hora e mostra o cartão (antes abria o app).
- **Vigia do leitor:** se uma captura ou o OCR não responder, o leitor se destrava sozinho em 20 s.
- **Vigia do copiloto:** notificação com a hora da última oferta e alarme quando passa 10 min sem ler.
- **Recusa automática (experimental):** recusa ofertas vermelhas após 5 s, com contador no cartão; funciona também no modo "Ler qualquer app".

### Tela preta OLED
- Cobre a tela inteira, inclusive barra de status e navegação, quando o serviço de acessibilidade está conectado.
- Sai no X cinza à direita; não liga sozinha com o app.

## Testes
55 testes de unidade na JVM (bordas do avaliador, alertas, parser, custos, meta do dia, vigia, resposta da IA e ofertas reais).

## Privacidade
Com o corretor IA desligado — o padrão — nenhuma requisição é feita: o serviço nem tenta abrir conexão sem uma API key configurada. A imagem capturada é reciclada após o reconhecimento; o texto só é guardado se você ligar "Guardar texto lido", e o log de treino só grava se você ligar "Registrar cada leitura". A chave da NVIDIA fica nas preferências do app, em texto simples — use uma chave só para teste e revogue depois.

## Desenvolvimento
Kotlin + Jetpack Compose nativo, sem dependências de runtime além do ML Kit. Build com Gradle 9.6 + AGP 9.4, `compileSdk 37`, `targetSdk 36`, `minSdk 30`.
