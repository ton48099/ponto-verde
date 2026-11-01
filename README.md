# Ponto Verde

Avaliador local de ofertas de corrida para Android. Lê a oferta que aparece na tela
dos apps de motorista, calcula R$/km e R$/h, pinta um cartão com a cor da oferta e
guarda tudo num histórico local, dia a dia.

**Pacote:** `com.pontoverde.app` · **Versão:** 0.1.0 · **Uso:** estritamente pessoal.

## Privacidade

O APK **não tem permissão de `INTERNET`** (a permissão que o ML Kit declara é removida
no manifesto). Nada do que o app lê pode sair do aparelho. A imagem capturada é
reciclada após o reconhecimento; o texto só é guardado se você ligar "Guardar texto
lido".

## Requisitos para compilar

| item | valor usado |
|---|---|
| JDK | 25 (o JBR do Android Studio serve) |
| Gradle | 9.6.0 |
| AGP | 9.4.0 (Kotlin embutido) |
| Android SDK | platform 37, build-tools 36.0.0 |
| Dispositivo | Android 11+ (API 30), por causa de `takeScreenshot` |

O projeto foi compilado com `compileSdk 37`, `targetSdk 36`, `minSdk 30`.

## Compilar

```powershell
# na raiz do projeto (ponto-verde/)

# primeira vez (precisa de internet para baixar Compose, ML Kit e dependências)
.\gradlew.bat :app:assembleDebug

# depois, offline
.\gradlew.bat :app:assembleDebug --offline

# testes do núcleo (JVM, sem aparelho)
.\gradlew.bat :app:testDebugUnitTest
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk` (~77 MB, por causa do modelo
de OCR embutido).

## Instalar

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.pontoverde.app/.MainActivity
```

## Configurar no celular

1. **Acessibilidade:** aba Copiloto → "Ativar copiloto" (o app oferece o atalho
   "Abrir acessibilidade") → Ponto Verde → ativar.
2. **Sobrepor:** Início → "Sobrepor" → permitir desenhar sobre outros apps.
3. Abra o Uber ou o 99 e espere uma oferta. O cartão aparece sobre a tela e a oferta
   entra no histórico do dia.

Sem oferta por perto? Início → **Calcular oferta** digita os números e mostra a mesma
avaliação, e o botão "Ver no cartão" desenha o cartão flutuante.

### Testar com um print

O leitor só observa o Uber e o 99. Para testar com um print aberto na galeria ou no
WhatsApp, ligue **Ajustes → Avançado → Opções de desenvolvedor → Ler qualquer app**.
Nesse modo o app lê a janela que está na frente (nunca a própria tela). Desligue depois
do teste.

### Print da oferta

Ligue **Ajustes → Dados → Salvar print das ofertas**. A imagem da oferta passa a ser
guardada junto do registro no histórico (armazenamento interno do app). No detalhe da
oferta, o botão **Baixar em Downloads** copia a imagem para
`Download/Ponto Verde/`, sem pedir permissão de armazenamento.

### Bolha flutuante

Enquanto o copiloto está ligado, uma bolha **PV** fica sobre os outros apps. Tocar abre
o Ponto Verde; arrastar muda a posição (ela é lembrada). Dentro do app a bolha some — e
com o copiloto desligado ela não aparece em lugar nenhum.

## Estrutura

```
app/src/main/java/com/pontoverde/app/
  core/        modelo, avaliador, alertas, parser, formatação — Kotlin puro, testável na JVM
  data/        SQLiteOpenHelper (histórico) + SharedPreferences (ajustes) + repositório
  capture/     serviço de acessibilidade, cartão flutuante (Views), bolha, prints, TTS
  ui/          Compose: tema, componentes, quatro abas, calculadora
app/src/test/  42 testes: bordas, alertas, parser, custos, meta do dia e ofertas reais
```

### Organização das telas

| aba | conteúdo |
|---|---|
| **Início** | "Meu dia": saldo líquido, bruto/despesas, ganho por km e hora, meta com custo, faturamento por app, gastos, km e horas — e **Dias salvos** |
| **Copiloto** | botão **Ativar/Desativar copiloto**, botão **Tela preta OLED**, status do serviço, resumo de hoje e última oferta |
| **Histórico** | ofertas **por dia**, com seletor de data, resumo do dia e "Limpar histórico" |
| **Ajustes** | cartões recolhíveis: Aparência, Regras, Custos, Cartão, Alertas, Dados e Avançado |

Em **Ajustes → Avançado → Opções de desenvolvedor** ficam: permissões, modo rápido,
ler qualquer app, avisar falha, guardar texto, calcular oferta, apagar histórico,
estatísticas e **Recusa automática (experimental)**.

## Meu dia, meta e dias salvos

A aba **Início** fecha o dia: saldo líquido (bruto − despesas), ganho por km e por
hora, faturamento por app, gastos, km rodados e horas trabalhadas.

A **meta de lucro** considera o custo do veículo. O que falta é convertido em km a
rodar pela margem real:

```
margem por km  = média de R$/km do dia − custo por km
km a rodar     = falta de líquido ÷ margem por km
falta de bruto = falta de líquido + custo por km × km a rodar
alvo de bruto  = bruto do dia + falta de bruto
```

Por isso o app mostra os três números juntos: quanto falta de **líquido**, quanto falta
de **faturamento** (que é maior que o buraco líquido, porque os km novos também
custam) e **quantos km** isso representa. Se a margem for zero ou negativa, o app avisa
em vez de dividir por zero.

**Salvar dia** guarda o fechamento do dia numa lista no fim da página. Um dia salvo pode
ser aberto (volta para aquela data, com o botão "hoje" no topo), editado e removido da
lista. **Zerar dia** limpa os lançamentos daquele dia, com confirmação.

**Saldo líquido como copinho de progresso:** o card do saldo líquido no topo da página
mostra um "copinho" que enche de baixo para cima conforme você bate a meta de lucro do
dia. No tema escuro o copinho começa preto e vai ficando verde (metade verde/metade
preto a 50%, todo verde quando bate a meta); no tema claro começa branco e fica todo
verde quando a meta é atingida. A cor vai do vermelho (longe da meta) ao amarelo e ao
verde, e quando a meta é batida o card fica com destaque verde nos dois temas.

## Custos e lucro

Ajustes → **Custos**: preço do litro, consumo (km/L), reserva de manutenção por km,
outros gastos e meta diária. Há um botão de valores típicos. Com isso o app calcula
`custo por km` e o cartão pode mostrar o **lucro**: valor − custo × km total. Ligue em
Ajustes → **Cartão → Lucro no cartão** (ou em Informações no cartão).

### Informações e tamanho do cartão

Ajustes → **Cartão → Informações no cartão** liga/desliga cada linha (R$/km, R$/h,
R$/min, só trecho, embarque, nota, lucro, tempo). O valor e a cor final aparecem
sempre. O **tamanho** (pequeno/médio/grande/extra grande), a posição e a opacidade
ficam no mesmo cartão de Ajustes.

### Modo tablet / segunda tela

Ligue em Ajustes → Cartão → **Modo tablet / segunda tela**. Nesse modo o app cria um
cartão **dentro da janela de cada app** (Uber e 99 ao mesmo tempo), respeitando a
posição escolhida, e usa o display da janela — inclusive tela externa (DeX/Smart
View). Desligado, volta ao cartão único no topo.

## Regras, alertas e aparência

Há **uma regra só**, que vale para Uber e 99: par ruim/bom de R$/km, de R$/h e de nota,
com passos finos. O embarque não tem cor própria — ele entra nos denominadores de
R$/km e R$/h — e o limite de embarque é um **alerta** em Ajustes → Alertas.

Cada alerta tem seu próprio interruptor (valor mínimo, embarque km/min, trecho, viagem,
paradas, passageiro novo). Desligar um alerta não muda a cor.

Ajustes → **Aparência**: **Claro**, **Escuro** ou **Sistema**. O tema escuro é feito
para OLED — fundo e cartões em preto puro, contornos verdes discretos e acentos neon
(verde, âmbar e vermelho) para as cores da oferta.

## Telas

Capturas reais no Motorola Edge 20 Pro, com o tema escuro, em `docs/telas/`:

| arquivo | tela |
|---|---|
| `tela-inicio.png` | Meu dia com saldo, meta e custo por km |
| `tela-dia.png` | Meu dia rolado: faturamento por app, gastos, km, horas e Dias salvos |
| `tela-historico.png` | histórico do dia com resumo e "Limpar histórico" |
| `tela-seletor-dia.png` | seletor de data (dias futuros desabilitados) |
| `tela-copiloto.png` | Copiloto: botão de ligar/desligar, tela preta OLED e o que ele já viu |
| `tela-preta-oled.png` | Tela preta OLED ativada com X de saída no canto direito |
| `tela-ajustes.png` | Ajustes em cartões recolhíveis |
| `tela-detalhe.png` | detalhe com as cores por critério |
| `tela-cartao.png` | cartão flutuante sobre a tela |
| `tela-cartao-print.png` | cartão lendo um print aberto na galeria (modo de teste) |
| `tela-cartao-centro.png` | cartão no topo central (posição configurável) |
| `tela-cartao-lucro.png` | cartão com lucro real (R$ 14 − custo de 7 km) |
| `tela-custos.png` | custos do veículo e custo por km |
| `tela-dev.png` | Opções de desenvolvedor |
| `tela-calculadora.png` | calculadora manual |

As capturas de cartão e de custos são do Galaxy S20 FE, no tema claro, e continuam
válidas para aquelas telas.

## O que já funciona

- Avaliação com as bordas corretas (`>=` nos limites bons, `>` nos ruins).
- **Embarque entra no cálculo**: R$/km e R$/h usam trecho + embarque (km e minutos).
- Histórico em SQLite **por dia**, com seletor de data, cores por critério e decisão
  aceitei/recusei.
- **Uma regra global** para Uber e 99, editável em passos finos.
- **Meu dia** com saldo, bruto/despesas, ganho por km/hora, faturamento por app, gastos,
  km, horas e **meta que desconta o custo por km**.
- **Salvar dia / dias salvos**, com abrir, editar e remover.
- Cartão flutuante arrastável, com opacidade, posição, tamanho e tempo configuráveis.
- Narração da cor final (TTS do sistema).
- Cada alerta com seu próprio interruptor (valor, embarque, trecho, viagem, paradas,
  passageiro novo); embarque bloqueado cala os alertas numéricos.
- **Custos do veículo** com padrão (etanol 4,19/L, 9 km/L, 0,25/km, meta 300) e **lucro
  real no cartão**, opcional.
- **Modo tablet / segunda tela**: um cartão dentro da janela de cada app, inclusive em
  tela externa (DeX/Smart View), e a bolha em todas as telas.
- Print da oferta no histórico, com download para `Download/Ponto Verde/`.
- Modo de teste para ler qualquer app (inclusive um print aberto na galeria).
- **Tema claro, escuro (OLED) ou o do sistema**, com cartões recolhíveis em Ajustes.
- Calculadora manual.
- **Saldo líquido como copinho de progresso** da meta de lucro do dia (verde quando bate).
- **Tela preta OLED** no Copiloto: cobre a tela principal de preto (status bar, navegação e notificações incluídas); toque no X à direita para sair.
- **Recusa automática (experimental)** em Ajustes → Avançado → Opções de desenvolvedor: recusa ofertas vermelhas após 5 s se você não tocar no card. O card mostra um contador regressivo ("Recusa automática em 5s...") para você saber que está prestes a recusar. Funciona também com prints (modo "Ler qualquer app") para você testar sem afetar sua taxa de aceite no Uber/99.

## Lacunas conhecidas

Ver [../paridade.md](../paridade.md) e [../diferencas.md](../diferencas.md).
Resumo: o parser de OCR ainda é heurístico e precisa de calibração com ofertas reais;
turno, recibos, backup e exportação ainda não existem.
