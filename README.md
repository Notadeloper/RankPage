This project is part of a group assignment, in which there were two main contributors including myself. The indexer, pagerank and query system/frontend as well as the websever is all my code. The other code (KVS, flame) I had implemented for homeworks thorughout the term, but ended up taking someone else's code for the final implementation.

This is a cloud-based search engine that can also be ran locally. It has multiple parts including a webserver loosely based on the spark web server, a sharded centralised scalable KVS store, a distributed data analytics engine (Flame) that is loosely based on Apache Spark, a fault-tolerant scalable crawler, a parallelised indexer and pagerank implementation, based on the original algorithm. This was interacted with through a frontend query interface, that would rank results based on results from indexing (e.g. tf/idf) and pagerank.

Instructions for Compilation:

WINDOWS:

The compile.bat script will compile crawler.jar, indexer.jar, pagerank.jar, and all the java file in the bin directory. Assumes that jsoup and tika are in the lib directory. The project can also be imported into Eclipse and built with Eclipse's build tools.

Regarding running the crawler:

The crawlerCompileAndRun.bat script will compile the relevant jar and java files and spawn the terminal instances with running kvs coordinator/workers and flame coordinator/workers.
The first argument to the crawler is a list of seed urls in the format "url1;url2;url3".

Example seed URLs:
1. https://www.wikipedia.org/
2. https://www.eater.com/maps/best-restaurants-sydney-australia

Run the following command to launch the crawler flame job:

```bash
java -cp bin cis5550.flame.FlameSubmit localhost:9000 crawler.jar cis5550.jobs.Crawler "https://www.wikipedia.org/;https://www.eater.com/maps/best-restaurants-sydney-australia
```

ON MACOS/LINUX

Run the compileAndLaunch.sh script on Linux/MacOS. Please ensure the third party .jar files are downloaded and placed into the /lib folder!

Run the following command to launch the crawler flame job:

```bash
java -cp bin cis5550.flame.FlameSubmit localhost:9000 crawler.jar cis5550.jobs.Crawler https://www.wikipedia.org/;https://www.eater.com/maps/best-restaurants-sydney-australia
```

Accessing localhost:800X should allow you to view the data of KVS worker X.

Once a crawler of suitable size has been created, for both Windows and MacOS, to then run the Indexer, do

```bash
java -cp bin cis5550.flame.FlameSubmit localhost:9000 indexer.jar cis5550.jobs.Indexer
```

Finally, run the PageRank

```bash
java -cp bin cis5550.flame.FlameSubmit localhost:9000 pagerank.jar cis5550.jobs.PageRank
```

Now, the frontend can be accessed at localhost:3000 which accepts queries and returns results.# RankPage
# RankPage
