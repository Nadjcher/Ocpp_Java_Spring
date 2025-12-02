@echo off
cls
echo ╔══════════════════════════════════════════════════════════════════╗
echo ║           BUILD AUTOMATIQUE - EVSE SIMULATOR EXE                ║
echo ╚══════════════════════════════════════════════════════════════════╝
echo.

:: Vérifier si pkg est installé
echo [1/7] Vérification de pkg...
where pkg >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo → pkg non trouvé, installation en cours...
    npm install -g pkg
    echo ✅ pkg installé !
) else (
    echo ✅ pkg déjà installé
)
echo.

:: Aller dans le dossier frontend
echo [2/7] Build du frontend React...
cd frontend
if not exist "node_modules" (
    echo → Installation des dépendances frontend...
    npm install
)
echo → Build de production...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Erreur lors du build frontend !
    pause
    exit /b 1
)
echo ✅ Frontend buildé dans dist/
echo.

:: Retour au dossier racine
cd ..

:: Aller dans le dossier perf-runner
echo [3/7] Préparation de perf-runner...
cd perf-runner

:: Sauvegarder l'ancien package.json si nécessaire
if not exist "package.json.backup" (
    echo → Sauvegarde de package.json original...
    copy package.json package.json.backup >nul
)
echo.

:: Vérifier si le nouveau package.json existe
echo [4/7] Mise à jour de package.json...
if exist "..\package.json.adapted" (
    copy ..\package.json.adapted package.json >nul
    echo ✅ package.json mis à jour
) else if exist "package.json.adapted" (
    copy package.json.adapted package.json >nul
    echo ✅ package.json mis à jour
) else (
    echo ⚠️  package.json.adapted non trouvé, utilisation du fichier existant
)
echo.

:: Installer les dépendances de production uniquement
echo [5/7] Installation des dépendances de production...
call npm ci --omit=dev
if %ERRORLEVEL% NEQ 0 (
    echo → Utilisation de npm install à la place...
    call npm install --omit=dev
)
echo ✅ Dépendances installées
echo.

:: Créer le dossier dist s'il n'existe pas
if not exist "dist" mkdir dist

:: Build de l'exécutable
echo [6/7] Création de l'exécutable...
echo → Cela peut prendre 2-5 minutes...
call npx pkg . --targets node18-win-x64 --compress GZip --output dist/evse-simulator.exe
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Erreur lors de la création de l'exe !
    echo.
    echo Tentative alternative...
    call npx pkg runner-http-api.js --targets node18-win-x64 --output dist/evse-simulator.exe
    if %ERRORLEVEL% NEQ 0 (
        echo ❌ Échec de la création de l'exécutable
        pause
        exit /b 1
    )
)
echo.

:: Vérifier le résultat
echo [7/7] Vérification du build...
if exist "dist\evse-simulator.exe" (
    for %%F in ("dist\evse-simulator.exe") do set size=%%~zF
    set /a sizemb=%size% / 1048576
    echo.
    echo ╔══════════════════════════════════════════════════════════════════╗
    echo ║                    ✅ BUILD RÉUSSI !                             ║
    echo ╚══════════════════════════════════════════════════════════════════╝
    echo.
    echo Fichier créé : dist\evse-simulator.exe
    echo Taille : ~%sizemb% MB
    echo.
    echo Pour tester :
    echo 1. Double-cliquez sur dist\evse-simulator.exe
    echo 2. Ouvrez http://localhost:8877 dans votre navigateur
    echo.
) else (
    echo ❌ L'exécutable n'a pas été créé
    echo Vérifiez les erreurs ci-dessus
)

echo.
echo Appuyez sur une touche pour fermer...
pause >nul