@echo off
cd /d "%~dp0"
echo A compilar o mod (jar)...
.\gradlew.bat jar 2>&1
if %errorlevel% equ 0 (
    echo.
    echo Compilacao bem sucedida!
    for /r build\libs %%i in (*.jar) do (
        echo JAR: %%i
    )
) else (
    echo.
    echo ERRO na compilacao - verifica as mensagens acima.
)
pause
