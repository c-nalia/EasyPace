@echo off
rem ===========================================================================
rem  EASYPACE - COMPILADOR
rem ---------------------------------------------------------------------------
rem  Uso (abra o Prompt de Comando nesta pasta, ou so clique duas vezes):
rem
rem     build.bat            gera o APK de teste (debug) em dist\
rem     build.bat jdk        baixa um JDK 21 compativel (so se precisar)
rem     build.bat keystore   cria a sua chave de assinatura (so uma vez)
rem     build.bat apk        gera o APK final ASSINADO em dist\  <== recomendado
rem     build.bat install    gera e instala pelo cabo USB (precisa de depuracao)
rem     build.bat test       roda os testes automatizados
rem     build.bat lint       roda a analise estatica do Android
rem     build.bat clean      apaga tudo que foi compilado
rem     build.bat reset      mata os daemons e limpa os caches do Gradle
rem     build.bat diag       roda o build em modo verboso e mostra o log real
rem     build.bat bump       soma 1 no versionCode (nova versao)
rem     build.bat bump 1.1.0 soma 1 no versionCode E troca o versionName
rem     build.bat help       mostra esta ajuda
rem
rem  VOCE NAO PRECISA DE DEPURACAO USB. Ela so serve para o "install".
rem  Para instalar sem ela: gere o APK, copie o arquivo para o celular e
rem  toque nele (o Android pede para autorizar "instalar apps desconhecidos").
rem
rem  O script acha sozinho o JDK e o Android SDK, e escreve o local.properties.
rem ===========================================================================
setlocal enabledelayedexpansion
title EasyPace - compilador

set "ROOT=%~dp0"
cd /d "%ROOT%"

set "CMD=%~1"
if "%CMD%"=="" set "CMD=debug"
if /i "%CMD%"=="help" goto :ajuda
if /i "%CMD%"=="-h" goto :ajuda
if /i "%CMD%"=="/?" goto :ajuda

echo.
echo  ===========================================================
echo    E A S Y P A C E   -   compilador                  140.85
echo  ===========================================================
echo.

rem  "jdk" vem antes da deteccao: e justamente o conserto dela.
if /i "%CMD%"=="jdk" goto :baixarJdk

call :acharJava
if errorlevel 1 goto :fim

call :acharSdk
if errorlevel 1 goto :fim

call :garantirWrapper
if errorlevel 1 goto :fim

call :lerVersao

if /i "%CMD%"=="keystore" goto :keystore
if /i "%CMD%"=="bump"     goto :bump
if /i "%CMD%"=="clean"    goto :clean
if /i "%CMD%"=="test"     goto :test
if /i "%CMD%"=="lint"     goto :lint
if /i "%CMD%"=="apk"      goto :release
if /i "%CMD%"=="release"  goto :release
if /i "%CMD%"=="install"  goto :install
if /i "%CMD%"=="reset"    goto :reset
if /i "%CMD%"=="diag"     goto :diag
if /i "%CMD%"=="debug"    goto :debug

echo  [X] Comando desconhecido: %CMD%
echo.
goto :ajuda


rem ===========================================================================
rem  ALVOS
rem ===========================================================================

:debug
echo  Compilando DEBUG  (versao %VNAME%, code %VCODE%)
echo.
call gradlew.bat :app:assembleDebug
if errorlevel 1 goto :falhouBuild
call :copiarApk "app\build\outputs\apk\debug\app-debug.apk" debug
if errorlevel 1 goto :fim
call :comoInstalar
goto :sucesso

:release
if not exist "keystore.properties" (
    echo  [X] Voce ainda nao tem uma chave de assinatura.
    echo.
    echo      Um APK sem assinatura NAO instala em celular nenhum.
    echo      Crie a sua chave agora - leva 10 segundos e e so uma vez:
    echo.
    echo          build.bat keystore
    echo.
    echo      Depois rode  build.bat apk  de novo.
    goto :fim
)
echo  Compilando RELEASE ASSINADO  (versao %VNAME%, code %VCODE%)
echo.
call gradlew.bat :app:assembleRelease
if errorlevel 1 goto :falhouBuild
if not exist "app\build\outputs\apk\release\app-release.apk" (
    echo  [X] Saiu um APK sem assinatura. Confira o keystore.properties.
    goto :fim
)
call :copiarApk "app\build\outputs\apk\release\app-release.apk" assinado
if errorlevel 1 goto :fim
call :comoInstalar
goto :sucesso

:keystore
echo  CRIACAO DA CHAVE DE ASSINATURA
echo  ---------------------------------------------------------
echo  Todo APK precisa ser assinado para o Android aceitar instalar.
echo  Esta chave e sua, fica so neste computador, e vale 27 anos.
echo.
echo  IMPORTANTE: guarde o arquivo easypace.jks e a senha. Se voce
echo  perder a chave, as proximas versoes nao conseguirao ATUALIZAR
echo  o app instalado - so desinstalando e instalando de novo.
echo.
if exist "easypace.jks" (
    echo  [!] Ja existe um easypace.jks nesta pasta.
    echo      Se voce quer mesmo criar outro, renomeie o antigo primeiro.
    goto :fim
)
set "PW="
set /p PW=  Escolha uma senha (minimo 6 caracteres, ENTER usa "easypace"): 
if "!PW!"=="" set "PW=easypace"
echo.
echo  Gerando...
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -noprompt ^
  -keystore "%ROOT%easypace.jks" -alias easypace ^
  -keyalg RSA -keysize 2048 -validity 10000 ^
  -storepass "!PW!" -keypass "!PW!" ^
  -dname "CN=EasyPace, OU=Uso pessoal, O=EasyPace, C=BR"
if errorlevel 1 (
    echo  [X] O keytool falhou. A senha precisa ter 6 caracteres ou mais.
    goto :fim
)
set "KSPATH=%ROOT%easypace.jks"
set "KSPATH=!KSPATH:\=/!"
> "%ROOT%keystore.properties" echo storeFile=!KSPATH!
>>"%ROOT%keystore.properties" echo storePassword=!PW!
>>"%ROOT%keystore.properties" echo keyAlias=easypace
>>"%ROOT%keystore.properties" echo keyPassword=!PW!
echo.
echo  [OK] Chave criada:
echo        %ROOT%easypace.jks
echo        %ROOT%keystore.properties
echo.
echo  Agora rode:   build.bat apk
goto :sucesso

:install
echo  Compilando DEBUG  (versao %VNAME%, code %VCODE%)
call gradlew.bat :app:assembleDebug
if errorlevel 1 goto :falhouBuild
call :copiarApk "app\build\outputs\apk\debug\app-debug.apk" debug
if errorlevel 1 goto :fim
echo  Instalando no aparelho...
set "ADB=%SDK%\platform-tools\adb.exe"
if not exist "%ADB%" (
    echo  [X] adb.exe nao encontrado em "%ADB%".
    echo      Instale o "Android SDK Platform-Tools" pelo SDK Manager.
    goto :fim
)
"%ADB%" install -r "%APKOUT%"
if errorlevel 1 (
    echo  [X] Falha ao instalar pelo cabo.
    echo      Se o seu celular nao tem depuracao USB, esqueca este comando:
    call :comoInstalar
    goto :fim
)
echo  [OK] Instalado. Procure o icone "EasyPace" no aparelho.
goto :sucesso

:test
echo  Rodando os testes automatizados...
call gradlew.bat :app:testDebugUnitTest
if errorlevel 1 (
    echo.
    echo  [X] Algum teste falhou. Relatorio detalhado em:
    echo      app\build\reports\tests\testDebugUnitTest\index.html
    goto :fim
)
echo  [OK] Todos os testes passaram.
goto :sucesso

:lint
call gradlew.bat :app:lintDebug
echo  Relatorio: app\build\reports\lint-results-debug.html
goto :sucesso

:clean
echo  Limpando...
call gradlew.bat clean
if exist "build" rmdir /s /q "build"
if exist "app\build" rmdir /s /q "app\build"
echo  [OK] Limpo.
goto :sucesso

:bump
set "NOVONOME=%~2"
echo  Versao atual: %VNAME%  (code %VCODE%)
set /a VCODENOVO=%VCODE%+1
powershell -NoProfile -Command ^
  "$p='app/build.gradle.kts';" ^
  "$t=Get-Content -Raw -Encoding UTF8 $p;" ^
  "$t=[regex]::Replace($t,'versionCode\s*=\s*\d+','versionCode = %VCODENOVO%');" ^
  "if('%NOVONOME%' -ne ''){ $t=[regex]::Replace($t,'versionName\s*=\s*\"[^\"]*\"','versionName = \"%NOVONOME%\"') }" ^
  "Set-Content -NoNewline -Encoding UTF8 $p $t"
if errorlevel 1 goto :falhouBuild
call :lerVersao
echo  [OK] Nova versao: %VNAME%  (code %VCODE%)
echo      Agora rode:  build.bat apk
goto :sucesso

:baixarJdk
echo  INSTALACAO DE UM JDK COMPATIVEL
echo  ---------------------------------------------------------
if exist "%ROOT%tools\jdk21\bin\java.exe" (
    echo  [i] Ja existe: %ROOT%tools\jdk21
    echo      Pode rodar  build.bat apk  direto.
    goto :sucesso
)
echo  Baixando o Eclipse Temurin JDK 21 para tools\jdk21 (~190 MB).
echo  Isso nao mexe no seu Android Studio nem no Java do sistema.
echo.
if not exist "%ROOT%tools" mkdir "%ROOT%tools"
set "JZIP=%ROOT%tools\jdk21.zip"
powershell -NoProfile -Command ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "$ProgressPreference='SilentlyContinue';" ^
  "Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile '%JZIP%'"
if not exist "%JZIP%" (
    echo  [X] Nao consegui baixar. Alternativa manual:
    echo      1. baixe o "JDK 21 - Windows x64 - .zip" em https://adoptium.net
    echo      2. extraia e renomeie a pasta para:  %ROOT%tools\jdk21
    echo         ^(dentro dela tem de existir bin\java.exe^)
    goto :fim
)
echo  Extraindo...
if exist "%ROOT%tools\_tmpjdk" rmdir /s /q "%ROOT%tools\_tmpjdk"
mkdir "%ROOT%tools\_tmpjdk"
tar -xf "%JZIP%" -C "%ROOT%tools\_tmpjdk" 2>nul
if errorlevel 1 powershell -NoProfile -Command "Expand-Archive -Force -Path '%JZIP%' -DestinationPath '%ROOT%tools\_tmpjdk'"
for /d %%D in ("%ROOT%tools\_tmpjdk\*") do move "%%~fD" "%ROOT%tools\jdk21" >nul
if exist "%ROOT%tools\_tmpjdk" rmdir /s /q "%ROOT%tools\_tmpjdk"
del "%JZIP%" >nul 2>&1
if not exist "%ROOT%tools\jdk21\bin\java.exe" (
    echo  [X] A extracao nao produziu tools\jdk21\bin\java.exe
    goto :fim
)
echo.
echo  [OK] JDK 21 instalado em %ROOT%tools\jdk21
echo      Ele sera usado automaticamente daqui para frente.
echo.
echo      Agora rode:   build.bat apk
goto :sucesso


:reset
rem  Zera tudo que o Gradle guarda entre execucoes. Nao apaga downloads
rem  (dependencias e distribuicao continuam em %USERPROFILE%\.gradle).
echo  Parando daemons do Gradle...
call gradlew.bat --stop
echo  Limpando caches locais do projeto...
if exist ".gradle" rmdir /s /q ".gradle"
if exist "build" rmdir /s /q "build"
if exist "app\build" rmdir /s /q "app\build"
echo  Limpando registro de daemons...
if exist "%USERPROFILE%\.gradle\daemon" rmdir /s /q "%USERPROFILE%\.gradle\daemon"
echo.
echo  [OK] Limpo. Rode agora:  build.bat apk
goto :sucesso

:diag
rem  Quando o build falha com uma mensagem que nao explica nada
rem  ("Could not receive a message from the daemon", por exemplo), a causa
rem  real esta no log do processo do daemon. Este alvo forca o build a rodar
rem  sem daemon reutilizavel e depois despeja esse log.
echo  DIAGNOSTICO
echo  ===========================================================
echo.
echo  --- JAVA_HOME -------------------------------------------
echo  %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version 2>&1
echo.
echo  --- memoria livre ---------------------------------------
powershell -NoProfile -Command "$os=Get-CimInstance Win32_OperatingSystem; '{0:N0} MB livres de {1:N0} MB' -f ($os.FreePhysicalMemory/1KB), ($os.TotalVisibleMemorySize/1KB)"
echo.
echo  --- gradlew --version -----------------------------------
call gradlew.bat --version
echo.
echo  --- build verboso, sem daemon ---------------------------
call gradlew.bat :app:assembleDebug --no-daemon --stacktrace
echo.
echo  --- ultimo log de daemon --------------------------------
set "DDIR=%USERPROFILE%\.gradle\daemon\8.11.1"
set "DLOG="
if exist "%DDIR%" for /f "delims=" %%f in ('dir /b /o-d "%DDIR%\*.log" 2^>nul') do if not defined DLOG set "DLOG=%DDIR%\%%f"
if defined DLOG (
    echo  Arquivo: !DLOG!
    echo.
    powershell -NoProfile -Command "Get-Content -Tail 60 -LiteralPath '!DLOG!'"
) else (
    echo  Nenhum log de daemon encontrado em %DDIR%
)
echo.
echo  ===========================================================
echo   Copie TUDO acima e mande para quem esta te ajudando.
echo  ===========================================================
goto :fim


rem ===========================================================================
rem  SUB-ROTINAS
rem ===========================================================================

:comoInstalar
echo.
echo  ----------------------------------------------------------
echo   COMO INSTALAR SEM DEPURACAO USB
echo  ----------------------------------------------------------
echo   1. Leve o APK para o celular. Qualquer caminho serve:
echo        - cabo USB no modo "Transferencia de arquivos" (MTP)
echo        - Google Drive / OneDrive
echo        - mandar para voce mesmo no WhatsApp ou Telegram
echo        - e-mail para voce mesmo
echo   2. No celular, abra o arquivo (Downloads ou Meus Arquivos).
echo   3. O Android vai avisar que a fonte e desconhecida. Toque em
echo      "Configuracoes" e permita a instalacao para aquele app
echo      (Arquivos, Chrome, WhatsApp - o que abriu o APK).
echo   4. Toque em Instalar. Se o Play Protect reclamar, escolha
echo      "Instalar mesmo assim" - o aviso e so porque o app nao
echo      veio da Play Store.
echo  ----------------------------------------------------------
exit /b 0

:acharJava
rem ---------------------------------------------------------------------------
rem  Escolhe um JDK que o Gradle 8.11 + plugin Android 8.7 saibam usar.
rem  A faixa util e Java 17 a 23:
rem    - abaixo de 17 o plugin Android nem roda;
rem    - de 24 para cima o plugin nao reconhece o numero da versao e o build
rem      morre com uma mensagem enigmatica (so o numero, tipo "25.0.2").
rem  O JBR que vem dentro do Android Studio costuma ser NOVO DEMAIS - por isso
rem  aqui cada candidato e medido, em vez de pegar o primeiro que aparecer.
rem ---------------------------------------------------------------------------
set "JH_ENV=%JAVA_HOME%"
set "JAVA_HOME="
set "JMELHOR="
set "JMELHORV="
set "JNOVODEMAIS="

rem  1) o JDK que o proprio script baixou, se existir
call :avaliarJava "%ROOT%tools\jdk21"
rem  2) o JAVA_HOME que ja estava no sistema
if defined JH_ENV call :avaliarJava "%JH_ENV%"
rem  3) instalacoes comuns
for %%D in (
  "%ProgramFiles%\Android\Android Studio\jbr"
  "%ProgramFiles%\Android\Android Studio\jre"
  "%LOCALAPPDATA%\Programs\Android Studio\jbr"
  "%ProgramFiles%\Android\Android Studio Preview\jbr"
) do call :avaliarJava "%%~D"
for /d %%D in (
  "%ProgramFiles%\Java\jdk*"
  "%ProgramFiles%\Eclipse Adoptium\jdk*"
  "%ProgramFiles%\Microsoft\jdk*"
  "%ProgramFiles%\Amazon Corretto\jdk*"
  "%ProgramFiles%\Zulu\zulu*"
  "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk*"
) do call :avaliarJava "%%~D"

if defined JMELHOR (
    set "JAVA_HOME=!JMELHOR!"
    echo  [i] JDK ..... !JMELHORV!  ^(!JMELHOR!^)
    exit /b 0
)

echo  [X] Nenhum JDK compativel encontrado ^(preciso de Java 17 a 23^).
if defined JNOVODEMAIS (
    echo.
    echo      Achei o Java !JNOVODEMAIS!
    echo      Ele e NOVO DEMAIS para a versao do Gradle usada neste projeto:
    echo      o build morre com uma mensagem que e so o numero da versao.
)
echo.
echo      Conserto automatico ^(baixa ~190 MB, uma unica vez^):
echo.
echo          build.bat jdk
echo.
echo      Ele instala um JDK 21 dentro de tools\jdk21, sem mexer em nada
echo      do resto do computador nem no seu Android Studio.
exit /b 1

:avaliarJava
rem  Recebe a pasta de um JDK. Se servir, guarda o caminho em JMELHOR.
if defined JMELHOR exit /b 0
set "CAND=%~1"
if not exist "!CAND!\bin\java.exe" exit /b 0
set "V="
set "M="
set "JTMP=%TEMP%\easypace_java.txt"
"!CAND!\bin\java.exe" -version > "!JTMP!" 2>&1
for /f tokens^=2^ delims^=^" %%v in ('findstr /i "version" "!JTMP!"') do if not defined V set "V=%%v"
del "!JTMP!" >nul 2>&1
if not defined V exit /b 0
for /f "tokens=1 delims=." %%m in ("!V!") do set "M=%%m"
if "!M!"=="1" exit /b 0
if !M! LSS 17 exit /b 0
if !M! GTR 23 (
    if not defined JNOVODEMAIS set "JNOVODEMAIS=!V!  em  !CAND!"
    exit /b 0
)
set "JMELHOR=!CAND!"
set "JMELHORV=!V!"
exit /b 0

:acharSdk
set "SDK="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platforms" set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platforms" set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk\platforms" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK if exist "%ProgramFiles%\Android\Sdk\platforms" set "SDK=%ProgramFiles%\Android\Sdk"
if not defined SDK if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platforms" set "SDK=%USERPROFILE%\AppData\Local\Android\Sdk"
if not defined SDK (
    echo  [X] Android SDK nao encontrado.
    echo      Abra o Android Studio uma vez e deixe ele baixar o SDK,
    echo      ou defina a variavel de ambiente ANDROID_HOME.
    exit /b 1
)
echo  [i] SDK ..... %SDK%
if not exist "%SDK%\platforms\android-35" (
    echo  [!] A API 35 nao esta instalada. O Gradle vai tentar baixar sozinho;
    echo      se ele reclamar de licenca, instale pelo Android Studio em
    echo      Settings ^> Languages ^& Frameworks ^> Android SDK ^> API 35.
)
> "%ROOT%local.properties" echo sdk.dir=!SDK:\=\\!
exit /b 0

:garantirWrapper
if exist "gradle\wrapper\gradle-wrapper.jar" exit /b 0
echo  [i] gradle-wrapper.jar ausente - baixando...
if not exist "gradle\wrapper" mkdir "gradle\wrapper"
powershell -NoProfile -Command ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'"
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo  [X] Nao consegui baixar o wrapper. Alternativa: abra a pasta no
    echo      Android Studio uma vez - ele gera o arquivo sozinho.
    exit /b 1
)
echo  [i] wrapper baixado.
exit /b 0

:lerVersao
set "VNAME=?"
set "VCODE=0"
for /f "tokens=2 delims==" %%v in ('findstr /r /c:"versionName *=" app\build.gradle.kts') do set "VNAME=%%v"
for /f "tokens=2 delims==" %%v in ('findstr /r /c:"versionCode *=" app\build.gradle.kts') do set "VCODE=%%v"
set "VNAME=%VNAME: =%"
set "VNAME=%VNAME:"=%"
set "VCODE=%VCODE: =%"
exit /b 0

:copiarApk
set "ORIGEM=%~1"
set "TIPO=%~2"
if not exist "%ORIGEM%" (
    echo  [X] APK nao foi gerado em %ORIGEM%
    exit /b 1
)
if not exist "dist" mkdir "dist"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmm"') do set "STAMP=%%i"
set "APKOUT=%ROOT%dist\EasyPace-%VNAME%-%TIPO%-%STAMP%.apk"
copy /y "%ORIGEM%" "%APKOUT%" >nul
echo.
echo  [OK] APK pronto:
echo        %APKOUT%
exit /b 0

:falhouBuild
echo.
echo  [X] A compilacao falhou. Leia a mensagem do Gradle acima.
echo      Dicas rapidas:
echo        - primeira compilacao baixa ~200 MB: precisa de internet
echo        - erro que e so um numero de versao (ex.: "25.0.2"): o Java
echo          esta novo demais. Rode  build.bat jdk
echo        - "SDK location not found": rode build.bat de novo
echo        - erro de memoria: reduza o -Xmx em gradle.properties
echo.
echo      Se a mensagem NAO explica nada (ex.: "Could not receive a
echo      message from the daemon"), faca nesta ordem:
echo          build.bat reset
echo          build.bat apk
echo      e, se ainda falhar:
echo          build.bat diag
goto :fim

:sucesso
echo.
echo  ===========================================================
echo    CONCLUIDO
echo  ===========================================================
goto :fim

:ajuda
echo.
echo   EASYPACE - COMPILADOR
echo.
echo     build.bat              gera o APK de teste (debug) em dist\
echo     build.bat jdk          baixa um JDK 21 compativel (so se precisar)
echo     build.bat keystore     cria a sua chave de assinatura (so uma vez)
echo     build.bat apk          gera o APK final ASSINADO  ^<== recomendado
echo     build.bat install      gera e instala pelo cabo (precisa de depuracao)
echo     build.bat test         roda os testes automatizados
echo     build.bat lint         analise estatica do Android
echo     build.bat clean        apaga tudo que foi compilado
echo     build.bat reset        mata daemons e limpa os caches do Gradle
echo     build.bat diag         build verboso + log real do daemon
echo     build.bat bump         soma 1 no versionCode
echo     build.bat bump 1.1.0   soma 1 no versionCode e troca o nome da versao
echo.
echo   Os APKs prontos ficam na pasta dist\.
echo   Depuracao USB NAO e necessaria: copie o APK para o celular
echo   e toque nele para instalar.
echo.

:fim
echo.
if "%~1"=="" pause
endlocal
