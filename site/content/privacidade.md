# Política de privacidade

> **Rascunho.** Esta política é concluída antes de o Trecos ser publicado no
> Google Play (versão 1.0). Até lá, ela descreve o comportamento planejado.
> Tradução da [versão em inglês](/privacy/); em caso de diferença, vale a versão em inglês.

O Trecos é um app de inventário da casa que funciona offline. Ele **não tem
servidor**, anúncios, análise de uso nem SDK de relatório de falhas. O
desenvolvedor nunca recebe seus dados.

## O que o app guarda

| Dado | Onde | Obrigatório |
|---|---|---|
| Casas, compartimentos, itens, valores, números de série, tags, campos personalizados | No seu celular | Só o que você digitar |
| Endereços das casas | No seu celular | Não |
| Fotos (sem dados de localização) | No seu celular | Não |
| Perfil: nome e e-mail | No seu celular | Não |
| Configurações | No seu celular | - |

## Serviços do Google

| Recurso | O que é compartilhado, e com quem | Quando |
|---|---|---|
| Sincronização com o Google Drive | Seu inventário e suas fotos, copiados para o **seu próprio** Google Drive (uma pasta visível `Trecos`; a chave de criptografia, se usada, na pasta oculta do app) | Só se você conectar uma conta Google e ativar a sincronização |
| Leitura de QR | As imagens da câmera são processadas no celular pelo leitor de códigos do Google Play Services | Quando você escaneia |
| Gorjetas | As compras passam pelo Google Play Billing | Só se você der uma gorjeta |
| Avaliação | A avaliação no app do Google Play | No máximo uma vez, se você quiser avaliar |

O Trecos pede acesso apenas aos arquivos do Drive que ele cria (`drive.file`) e
à sua pasta oculta do app (`drive.appdata`).

### O que a sincronização coloca no seu Drive

Quando você conecta a sincronização, uma pasta visível **Trecos** no seu Meu
Drive guarda:

- os dados de cada casa, incluindo o que está na lixeira, em um arquivo por sincronização;
- um arquivo pequeno por aparelho, com a última sincronização dele;
- suas fotos em tamanho original, cada uma guardada uma vez.

Cada registro de sincronização também leva o nome e o e-mail da sua conta
Google, o nome do aparelho (que você pode editar) e um identificador do
aparelho gerado pelo app. Ele nunca registra um identificador de hardware nem
a sua localização.

O Drive guarda as **30 últimas sincronizações** de cada aparelho; as mais
antigas são apagadas automaticamente. Desconectar interrompe a sincronização e
mantém tudo no seu celular. Para remover os dados do Drive, apague a pasta
Trecos no Drive.

## O que o Trecos não faz

- Não coleta localização.
- Não rastreia, não cria perfis e não vende dados.
- Não pede permissão de câmera nem de armazenamento: as fotos vêm da câmera do
  sistema ou do seletor de fotos do Android.

## Seus direitos (LGPD)

Todos os dados ficam sob o seu controle no seu aparelho: edite ou apague no
app, exporte, ou desinstale o app para removê-los. Dados no seu Google Drive
são apagados no próprio Drive. Dúvidas: abra uma issue em
<https://github.com/vcasadei/trecos/issues> ou escreva para
<hello@trecos.app>.

## Alterações

As alterações desta política ficam registradas no histórico do arquivo
`PRIVACY.md` e no `CHANGELOG.md`.
