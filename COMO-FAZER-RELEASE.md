# Como criar o repositório e release do Ponto Verde no GitHub

## Passo 1: Criar o repositório no GitHub

1. Acesse [https://github.com/new](https://github.com/new)
2. **Repository name:** `ponto-verde`
3. **Description:** (opcional) Avaliador local de ofertas de corrida para Android
4. **Visibility:** Público ou Privado (como preferir)
5. **Initialize this repository with:** NÃO marque nada (não deixe criar README, .gitignore ou license)
6. Clique em **Create repository**

## Passo 2: Enviar o código para o GitHub

No terminal (PowerShell), na pasta `ponto-verde/`:

```powershell
# Inicializar git (se ainda não fez)
git init
git branch -M main

# Adicionar tudo
git add .
git commit -m "chore: release Ponto Verde 0.1.0"

# Adicionar o remote (substitua SEU_USUARIO pelo seu usuário do GitHub)
git remote add origin https://github.com/SEU_USUARIO/ponto-verde.git

# Enviar
git push -u origin main
```

## Passo 3: Criar a Release com o APK

1. No seu repositório no GitHub, clique em **Releases** (na barra lateral direita)
2. Clique em **Draft a new release**
3. Preencha:
   - **Tag version:** `v0.1.0`
   - **Release title:** `Ponto Verde 0.1.0`
   - **Describe this release:** cole o conteúdo do arquivo `RELEASE_NOTES.md`
4. Arraste o arquivo `PontoVerde_0.1.0.apk` (está na raiz do projeto) para **Attach files**
5. Marque **Set as the latest release** (opcional)
6. Clique em **Publish release**

## Arquivos importantes

| Arquivo | Onde está | Para quê |
|---------|-----------|----------|
| `PontoVerde_0.1.0.apk` | Raiz do projeto | Instalar no celular |
| `RELEASE_NOTES.md` | Raiz do projeto | Notas da release (colar no GitHub) |
| `criar-release-github.bat` | Raiz do projeto | Script para automatizar o envio |

## Próximos passos

Depois da release publicada, qualquer pessoa pode baixar o APK diretamente da página de Releases do seu repositório GitHub e instalar no Android.
