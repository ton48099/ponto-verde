@echo off
cd /d "%~dp0"

echo ========================================
echo  PONTO VERDE - PREPARAR RELEASE GITHUB
echo ========================================
echo.

REM Verificar se o APK existe
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ERRO: APK nao encontrado!
    echo Rode primeiro: .\gradlew.bat :app:assembleDebug --offline
    pause
    exit /b 1
)

REM Copiar APK com nome descritivo
copy "app\build\outputs\apk\debug\app-debug.apk" "PontoVerde_0.1.0.apk" >nul 2>&1
echo [1/6] APK copiado: PontoVerde_0.1.0.apk

REM Inicializar repositorio git (se ainda nao existe)
if not exist ".git\config" (
    echo [2/6] Inicializando repositorio git...
    git init
    git branch -M main
) else (
    echo [2/6] Repositorio git ja existe.
)

REM Adicionar arquivos
echo [3/6] Adicionando arquivos ao git...
git add .
git commit -m "chore: release Ponto Verde 0.1.0

- Versao inicial do Ponto Verde 0.1.0
- Avaliador local de ofertas de corrida para Android
- Historico em SQLite por dia
- Uma regra global para Uber e 99
- Meu dia com meta custo-aware
- Salvar dia / dias salvos
- Cartao flutuante configuravel
- Tema claro/escuro/sistema
- Tela preta OLED no Copiloto
- Recusa automática (experimental) de ofertas vermelhas

APK: PontoVerde_0.1.0.apk" >nul 2>&1

if %errorlevel% neq 0 (
    echo   Aviso: commit falhou (pode ja ter sido feito)
)

REM Adicionar remote (substitua SEU_USUARIO pelo seu usuario do GitHub)
echo [4/6] Para criar o repositorio no GitHub:
echo   1. Acesse https://github.com/new
echo   2. Nome do repositorio: ponto-verde
echo   3. Deixe PUBLICO ou PRIVADO (como preferir)
echo   4. NAO inicialize com README (ja temos um)
echo   5. Clique em "Create repository"
echo.
echo   Depois copie a URL do repositorio e cole aqui:
set /p REMOTE_URL="URL do repositorio GitHub: "

if not "%REMOTE_URL%"=="" (
    git remote remove origin 2>nul
    git remote add origin "%REMOTE_URL%"
    echo [5/6] Remote adicionado: %REMOTE_URL%
)

REM Enviar para o GitHub
echo [6/6] Enviando para o GitHub...
git push -u origin main

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo  REPOSITORIO CRIADO COM SUCESSO!
    echo ========================================
    echo.
    echo Agora crie a Release no GitHub:
    echo   1. Acesse o repositorio no GitHub
    echo   2. Clique em "Releases" na barra lateral direita
    echo   3. Clique em "Draft a new release"
    echo   4. Tag version: v0.1.0
    echo   5. Release title: Ponto Verde 0.1.0
    echo   6. Cole as notas abaixo no campo de descricao
    echo   7. Arraste o arquivo PontoVerde_0.1.0.apk para "Attach files"
    echo   8. Marque "Set as the latest release" se desejar
    echo   9. Clique em "Publish release"
    echo.
    echo Notas da release estao em: RELEASE_NOTES.md
    echo APK esta em: PontoVerde_0.1.0.apk
) else (
    echo.
    echo ERRO ao enviar para o GitHub.
    echo Verifique se voce tem permissao de escrita no repositorio.
)

pause
