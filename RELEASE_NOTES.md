# Ponto Verde 0.1.0

## Sobre
Avaliador local de ofertas de corrida para Android. Lê a oferta que aparece na tela dos apps de motorista (Uber, 99, inDrive, iFood, Taxi Machine, Regional), calcula R$/km e R$/h, pinta um cartão com a cor da oferta e guarda tudo num histórico local, dia a dia.

## Instalação
Baixe o APK abaixo e instale no seu Android (requer Android 11+).

## Permissões necessárias
- **Acessibilidade:** para ler ofertas dos apps de motorista
- **Sobrepor janelas:** para mostrar o cartão flutuante sobre a tela
- **Armazenamento:** para salvar prints das ofertas (opcional)

## Novidades na versão 0.1.0
- Avaliação com bordas corretas (`>=` nos limites bons, `>` nos ruins)
- Histórico em SQLite por dia, com seletor de data
- Uma regra global para Uber e 99, editável em passos finos
- Meu dia com saldo, bruto/despesas, ganho por km/hora, meta com desconto de custo por km
- Salvar dia / dias salvos, com abrir, editar e remover
- Cartão flutuante arrastável, com opacidade, posição e tempo configuráveis
- Cada alerta com seu próprio interruptor
- Custos do veículo com padrão e lucro real no cartão (opcional)
- Modo tablet / segunda tela
- Tema claro, escuro (OLED) ou o do sistema
- Calculadora manual
- Saldo líquido como copinho de progresso da meta de lucro
- Tela preta OLED no Copiloto (evita aquecimento)
- Recusa automática (experimental) de ofertas vermelhas após 5 s

## Privacidade
O APK não tem permissão de INTERNET. Nada do que o app lê pode sair do aparelho. A imagem capturada é reciclada após o reconhecimento; o texto só é guardado se você ligar "Guardar texto lido".

## Desenvolvimento
O projeto foi desenvolvido com Kotlin + Jetpack Compose nativo. Sem dependências de runtime — só TypeScript e @types/node como dev (para o avaliador de ofertas Zeca).
