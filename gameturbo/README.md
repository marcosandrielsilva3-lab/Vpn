# Game Turbo

Aplicativo Android nativo, compilado pelo GitHub Actions no mesmo repositório usado pelo projeto VPN.

Funções implementadas:
- leitura de modelo, versão do Android, RAM disponível, bateria e taxa de atualização da tela;
- atalhos de abertura do Roblox e do Free Fire, quando instalados;
- seleção e salvamento de perfil Roblox/Free Fire com dicas de configuração dentro do jogo;
- bolinha flutuante arrastável com atalhos para abrir os jogos ou o painel;
- cronômetro de sessão executado por serviço em primeiro plano, com notificação ao terminar;
- opção para manter a tela acesa enquanto o painel do Game Turbo estiver aberto.

Limites do Android:
- um aplicativo comum não pode forçar FPS, overclock, alterar a GPU de outro jogo ou limpar à força a memória de outros aplicativos;
- a taxa em Hz apresentada é a capacidade/taxa atual reportada pelo display, não FPS medido dentro de um jogo;
- a sobreposição e as notificações exigem autorização do usuário no Android.


## Ferramentas de otimização adicionadas
- limpeza imediata apenas do cache temporário do próprio Game Turbo;
- solicitação oficial ao Android para limpar caches de apps, após concessão voluntária de "Acesso a todos os arquivos" e confirmação na interface do sistema;
- abertura do gerenciador de armazenamento do Android;
- atalhos para configurações de aplicativos e bateria;
- acesso, pela bolinha flutuante, à limpeza rápida do cache do Game Turbo e ao gerenciador de apps.

A limpeza global de cache é controlada pelo Android. O Game Turbo não apaga dados pessoais, logins ou arquivos dos outros apps. Encerrar apps de terceiros automaticamente não é permitido para um aplicativo comum; o usuário pode gerenciar ou forçar a parada individualmente na tela oficial de aplicativos.
