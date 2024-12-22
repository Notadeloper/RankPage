@echo off
:: Script for WINDOWS users

del *.jar

:: Compile and create Crawler.jar
javac -cp "lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar" -d bin --source-path src src\cis5550\jobs\Crawler.java
jar cf crawler.jar -C bin cis5550\jobs\Crawler.class

:: Compile and create Indexer.jar
javac -cp "lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar" -d bin --source-path src src\cis5550\jobs\Indexer.java
jar cf indexer.jar -C bin cis5550\jobs\Indexer.class

:: Compile and create PageRank.jar
javac -cp "lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar" -d bin --source-path src src\cis5550\jobs\PageRank.java
jar cf pagerank.jar -C bin cis5550\jobs\PageRank.class

:: Compile all Java files
javac -cp "lib\jsoup-1.18.3.jar;lib\tika-app-3.0.0.jar" --source-path src -d bin src\cis5550\external\*.java src\cis5550\flame\*.java src\cis5550\frontend\*.java src\cis5550\generic\*.java src\cis5550\jobs\*.java src\cis5550\kvs\*.java src\cis5550\test\*.java src\cis5550\tools\*.java src\cis5550\webserver\*.java

endlocal
