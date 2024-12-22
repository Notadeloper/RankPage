#!/bin/bash

PWD=`pwd`
kvsWorkers=1 # number of kvs workers to launch
flameWorkers=3 # number of flame workers to launch

rm *.jar

# Compile and create Crawler.jar
javac -cp lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar -d bin --source-path src $(find src/cis5550/jobs -name '*.java')
sleep 1
jar cf crawler.jar -C bin cis5550/jobs
sleep 1

# Compile and create Indexer.jar
javac -cp lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar -d bin --source-path src src/cis5550/jobs/Indexer.java
sleep 1
jar cf indexer.jar bin/cis5550/jobs/Indexer.class
sleep 1

# Compile and create PageRank.jar
javac -cp lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar -d bin --source-path src src/cis5550/jobs/PageRank.java
sleep 1
jar cf pagerank.jar bin/cis5550/jobs/PageRank.class
sleep 1


CLASSPATH=$(find lib -name '*.jar' | tr '\n' ':')

# Compile the Java files
javac -cp "$CLASSPATH" --source-path src -d bin $(find src -name '*.java')

# Launch KVS Coordinator
echo "cd '$(PWD)'; java -cp bin:lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar cis5550.kvs.Coordinator 8000" > kvscoordinator.sh
chmod +x kvscoordinator.sh
open -a Terminal kvscoordinator.sh

sleep 2

# Launch KVS Workers
for i in `seq 1 $kvsWorkers`
do
    dir=worker$i
    if [ ! -d $dir ]
    then
        mkdir $dir
    fi
    echo "cd '$(PWD)'; java -cp bin:lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar cis5550.kvs.Worker $((8000+$i)) $dir localhost:8000" > kvsworker$i.sh
    chmod +x kvsworker$i.sh
    open -a Terminal kvsworker$i.sh
done

# Launch Flame Coordinator
echo "cd '$(PWD)'; java -cp bin:lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar cis5550.flame.Coordinator 9000 localhost:8000" > flamecoordinator.sh
chmod +x flamecoordinator.sh
open -a Terminal flamecoordinator.sh

sleep 2

# Launch Flame Workers
for i in `seq 1 $flameWorkers`
do
    echo "cd '$(PWD)'; java -cp bin:lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar cis5550.flame.Worker $((9000+$i)) localhost:9000" > flameworker$i.sh
    chmod +x flameworker$i.sh
    open -a Terminal flameworker$i.sh
done

# Launch SearchHandler diagnostics mode in a New Terminal
echo "cd '$(pwd)'; java -cp bin:lib/jsoup-1.18.3.jar:lib/tika-app-3.0.0.jar cis5550.frontend.SearchHandlerDiagnostics 3000 localhost:8000" > searchhandlerdiagnostics.sh
chmod +x searchhandlerdiagnostics.sh
open -a Terminal searchhandlerdiagnostics.sh

echo "All services have been launched in separate Terminal windows."
