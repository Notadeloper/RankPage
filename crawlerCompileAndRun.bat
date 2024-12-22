@echo off
:: Script for WINDOWS users

set kvsWorkers=1
set flameWorkers=2

:: Clean up from previous runs
rmdir /s /q worker1
rmdir /s /q worker2
rmdir /s /q worker3
rmdir /s /q worker4
del *.jar

:: Compile and create Crawler.jar
javac -cp "lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar" -d bin --source-path src src\cis5550\jobs\Crawler.java
jar cf crawler.jar -C bin cis5550\jobs\Crawler.class

:: Compile all Java files
javac -cp "lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar" --source-path src -d bin src\cis5550\external\*.java src\cis5550\flame\*.java src\cis5550\frontend\*.java src\cis5550\generic\*.java src\cis5550\jobs\*.java src\cis5550\kvs\*.java src\cis5550\test\*.java src\cis5550\tools\*.java src\cis5550\webserver\*.java

:: Launch KVS Coordinator
(
    echo cd %cd%
    echo java -cp bin;lib\webserver.jar;lib\kvs.jar;lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar cis5550.kvs.Coordinator 8000
) > kvscoordinator.bat
start cmd.exe /k kvscoordinator.bat

:: Launch KVS Workers
setlocal enabledelayedexpansion
for /l %%i in (1,1,%kvsWorkers%) do (
    set "dir=worker%%i"
    if not exist !dir! mkdir !dir!
    (
        echo cd %cd%\!dir!
        echo java -cp ..\bin;..\lib\webserver.jar;..\lib\kvs.jar;lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar cis5550.kvs.Worker 800%%i !dir! localhost:8000
    ) > kvsworker%%i.bat
    start cmd.exe /k kvsworker%%i.bat
)

:: Launch Flame Coordinator
(
    echo cd %cd%
    echo java -cp bin;lib\webserver.jar;lib\kvs.jar;lib\flame.jar;lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar cis5550.flame.Coordinator 9000 localhost:8000
) > flamecoordinator.bat
start cmd.exe /k flamecoordinator.bat

timeout /t 1 /nobreak

:: Launch Flame Workers
for /l %%i in (1,1,%flameWorkers%) do (
    (
        echo cd %cd%
        echo java -cp bin;lib\webserver.jar;lib\kvs.jar;lib\flame.jar;lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar cis5550.flame.Worker 900%%i localhost:9000
    ) > flameworker%%i.bat
    start cmd.exe /k flameworker%%i.bat
)

endlocal
