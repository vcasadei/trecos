# Códigos QR

Cole um código QR impresso numa caixa e leia-o para abrir a caixa no Trecos.

## Códigos

Cada item e compartimento pode ter um código QR: qualquer texto de até 256
caracteres. Ao criar um e deixar o código vazio, o Trecos usa o nome como código,
a menos que outra coisa na mesma casa já tenha esse código; nesse caso, salva sem
código e avisa. Renomear nunca muda o código, então o que você já imprimiu
continua funcionando.

Um código só pode ser usado uma vez por casa. Se você digitar um que já está em
uso, o formulário mostra quem o tem, com **Abrir** para ir até lá. O mesmo
código pode existir em casas diferentes.

## Mostrar, compartilhar e imprimir

- Num compartimento, toque no código abaixo do nome. Num item, toque em
  **Código QR** nos detalhes. O código abre em tamanho grande, com o texto
  embaixo.
- **Compartilhar** envia o código como imagem para qualquer app. **Imprimir**
  abre a janela de impressão do sistema.
- Para imprimir vários de uma vez, toque e segure uma linha, selecione as outras
  (ou **Selecionar tudo**) e toque no ícone de impressão. Todos vão num único
  trabalho de impressão, 12 por página. O que não tem código fica de fora, e o
  Trecos diz quantos.
- O menu ⋮ de um compartimento também tem **Imprimir QR**.

## Leitura

Toque no botão QR da Busca. O Trecos procura o código em todas as casas:

- Um resultado: a tela dele abre.
- Resultados em várias casas: você escolhe a casa.
- Nenhum resultado: você pode criar um item ou compartimento com esse código e
  nome, e depois escolher onde fica.
- Um resultado na lixeira: você pode restaurá-lo.

Códigos de outros programas também funcionam: o Trecos usa o texto lido
exatamente, só tirando espaços em volta. Maiúsculas e minúsculas contam.

Num formulário de item ou compartimento, o botão de leitura no campo **Código
QR** preenche o código, e o nome também se ainda estiver vazio.

A leitura usa o Google Play Services e não pede permissão de câmera. Na primeira
vez, o leitor pode precisar de um download único, então leia um código com
internet. Sem ele, você ainda pode digitar um código na Busca.
