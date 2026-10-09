# Primeiros passos

O Trecos registra o que você tem, onde está guardado e quanto vale. Funciona
sem internet e mantém tudo no seu celular.

## Instalação

Até a versão 1.0, o Trecos é instalado pelo GitHub em vez do Google Play
("sideload").

1. No celular, abra <https://github.com/vcasadei/trecos/releases> e escolha a versão mais recente.
2. Baixe `trecos-<versão>-universal.apk` (ou o do processador do seu celular, geralmente `arm64-v8a`).
3. Abra o arquivo. O Android pede para permitir instalações pelo navegador: permita para esta instalação.
4. Toque em **Instalar**. As próximas versões se instalam por cima do mesmo jeito e mantêm seus dados.

### Confira se o download é autêntico (opcional)

Cada versão traz nas notas um arquivo `SHA256SUMS` e a **impressão digital do
certificado de assinatura**. Em um computador com o Android SDK:

```sh
sha256sum -c SHA256SUMS
apksigner verify --print-certs trecos-<versão>-universal.apk
```

O primeiro comando deve mostrar `OK`, e o `SHA-256 digest` do segundo deve ser
igual à impressão digital das notas. Todas as versões do Trecos são assinadas
com a mesma chave; o Android recusa uma atualização assinada com outra.

## Primeiros passos no app

| Passo | Como |
|---|---|
| Dar nome ao primeiro lugar | Na primeira abertura, confirme "Minha casa" ou digite outro nome |
| Adicionar um compartimento | Toque em **+** e depois em **Compartimento**: um cômodo, prateleira, caixa ou bolsa. Compartimentos podem ficar dentro de compartimentos |
| Adicionar um item | Toque em **+** dentro de um compartimento e depois em **Item**. Só o nome é obrigatório |
| Adicionar vários itens | Use **Salvar + novo**: o próximo formulário continua no mesmo compartimento |
| Adicionar outra casa | Com uma casa, toque no nome dela no topo e depois em **Adicionar casa**. Com duas ou mais, toque em **Adicionar casa** na lista de casas |
| Navegar | A barra inferior tem Buscar, Início e Ajustes. Tocar numa aba sempre abre a tela principal dela: tocar em Início de dentro de um compartimento ou item volta direto para o Início, ou para a lista de casas |
| Trocar de casa | Com duas ou mais casas, o Início lista todas: volte para a lista e toque em outra casa. O app abre na casa usada por último |
| Subir de nível | Toque em qualquer nível do caminho, como "Apartamento > Escritório" |
| Mudar a lista | Toque no botão de visualização no topo: compacta ou detalhada. A escolha vale para o app todo |

## Valores

O total de um item é quantidade × preço unitário. O valor de um compartimento
soma tudo o que está dentro dele, em qualquer nível. Você pode digitar um valor
manual no formulário do compartimento; ele passa a contar para os
compartimentos acima. **Remover valor manual** volta ao valor automático. Itens
sem preço contam como zero, e o compartimento mostra quantos são.

## Fotos

Cada item, compartimento ou casa pode ter até 3 fotos. No formulário, toque em
**Adicionar foto**: na primeira vez, escolha Câmera ou Galeria (marque
**Lembrar minha escolha** para não ser perguntado de novo). Toque em uma foto
para torná-la a principal ou removê-la, ou toque e segure para arrastar e
reordenar. As fotos são guardadas pequenas (1920 px) e sem dados de
localização; o Trecos nunca precisa de permissão de câmera ou armazenamento.
