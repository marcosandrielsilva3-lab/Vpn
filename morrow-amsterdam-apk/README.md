# Subway Surfers Amsterdam — APK Android

Projeto Android simples que abre o endereço original do jogo dentro de uma WebView, sem alterar os arquivos ou a jogabilidade do site.

**Endereço carregado:**
`https://morrowplay.com/play/subway-surfers/amsterdam/?edition=amsterdam&mode=original`

## Baixar
Quando o GitHub Actions concluir a compilação, o APK instalável será publicado na seção **Releases** deste repositório com o nome `Subway-Amsterdam.apk`.

## Observações
- Requer conexão com a internet.
- O jogo continua sendo executado pelo site; este APK é um contêiner Android/WebView, não uma versão nativa ou offline.
- O desempenho depende do site, do Android System WebView e do aparelho. Transformar a página em APK não garante eliminar travamentos.
- O projeto usa apenas o endereço informado e não modifica o site original.

## Compilar manualmente
Com Android SDK instalado e Gradle 8.9:
```bash
gradle --no-daemon assembleDebug
``
O APK de depuração será gerado em `app/build/outputs/apk/debug/app-debug.apk`.
