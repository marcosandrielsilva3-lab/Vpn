# VPN Local Demo

Aplicativo Android de demonstração que solicita a autorização oficial de VPN do Android e cria uma interface VPN local de teste. Não conecta a servidor externo, não altera o IP público e não oferece privacidade ou proteção de tráfego.

O APK será disponibilizado após a compilação automática do projeto.

## Versão 1.2 — cache e tela
- A limpeza em massa usa a ação pública ACTION_CLEAR_APP_CACHE e trata os retornos de sucesso, erro de I/O e cancelamento separadamente.
- A API Android exige MANAGE_EXTERNAL_STORAGE e só alcança os diretórios de cache externo dos apps; ela não dá acesso ao cache privado interno de cada aplicativo.
- Em versões anteriores ao Android 11, o aplicativo abre o gerenciamento de apps em vez de alegar que limpou todos os caches.
- O atalho de resolução abre as configurações de tela do Android. O próprio fabricante decide se oferece ajuste de resolução ao usuário.
- Não existe API Android pública para um app comum rebaixar à força as texturas ou a resolução interna de jogos de terceiros. O Game Turbo não mostra controles fictícios para isso.


### Diagnóstico da limpeza (1.2.1)
O painel exibe se o acesso especial está autorizado. A tentativa de limpeza só é considerada concluída quando a atividade do Android retorna RESULT_OK; cancelamento e EIO são informados separadamente. A ação pública do Android limpa diretórios de cache externo dos apps, não os diretórios privados internos.
