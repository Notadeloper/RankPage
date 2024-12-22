package cis5550.flame;

import java.util.*;
import java.io.*;
import java.net.URLDecoder;

import static cis5550.webserver.Server.*;
import cis5550.tools.Hasher;
import cis5550.tools.Identifier;
import cis5550.tools.Logger;
import cis5550.tools.Serializer;
import cis5550.flame.FlameContext.RowToString;
import cis5550.flame.FlamePairRDD.PairToPairIterable;
import cis5550.flame.FlamePairRDD.PairToPair;
import cis5550.flame.FlamePairRDD.PairToStringIterable;
import cis5550.flame.FlamePairRDD.TwoStringsToString;
import cis5550.flame.FlameRDD.IteratorToIterator;
import cis5550.flame.FlameRDD.StringToBoolean;
import cis5550.flame.FlameRDD.StringToIterable;
import cis5550.flame.FlameRDD.StringToPair;
import cis5550.flame.FlameRDD.StringToPairIterable;
import cis5550.flame.FlameRDD.StringToString;
import cis5550.kvs.*;

class Worker extends cis5550.generic.Worker {
    private static final Logger logger = Logger.getLogger(Worker.class);
    private static final int batchSize = 100;

    static File myJAR;

    public static void main(String args[]) {
        if (args.length != 2) {
            System.err.println("Syntax: Worker <port> <coordinatorIP:port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String server = args[1];

        // Setting a workerID
        Worker.workerID = UUID.randomUUID().toString().substring(0, 10);

        // Integrating kvs code
        Worker.portNumber = port;
        Worker.coordinator = server;
        startPingThread();

        myJAR = new File("__worker" + port + "-current.jar");

        port(port);

        post("/useJAR", (request, response) -> {
            FileOutputStream fos = new FileOutputStream(myJAR);
            fos.write(request.bodyAsBytes());
            fos.close();
            return "OK";
        });

        registerPostContextFromTable();

        registerPostRDDDistinct();
        registerPostRDDFold();
        registerPostRDDFlatMap();
        registerPostRDDFlatMapToPair();
        registerPostRDDMapToPair();

        registerPostPairRDDFoldByKey();
        registerPostPairRDDFlatMap();
        registerPostPairRDDFlatMapToPair();
        registerPostPairRDDmapToPair();
        registerPostPairRDDJoin();

        // HW6 EC
        registerPostRDDIntersectTo();
        registerPostRDDSample();
        registerPostRDDGroupBy();

        // HW7 EC
        registerPostRDDFilter();
        registerPostPairRDDFilter();
        registerPostRDDMapPartitions();
        registerPostPairRDDCogroup();
        registerPostRDDInvert();
    }

    private static void registerPostContextFromTable() {
        post("/context/fromTable", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            RowToString lambda = (RowToString) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String result = lambda.op(row);
                    if (result != null) {
                        kvs.put(outputTableName, row.key(), "value", result);
                    }
                }

                res.status(200, "OK");
                return null;

            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive, kvsCoordinator);
                logger.error("Encountered an error duing /context/fromTable " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostRDDDistinct() {
        post("/rdd/distinct", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<Row> rowBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String value = row.get("value");
                    // kvs.put(outputTableName, Hasher.hash(value), "value", value);
                    Row newRow = new Row(Hasher.hash(value));
                    newRow.put("value", value);
                    rowBuffer.add(newRow);

                    if (rowBuffer.size() == Worker.batchSize) {
                        kvs.putRowBatch(outputTableName, rowBuffer);
                        rowBuffer.clear();
                    }
                }

                if (rowBuffer.size() > 0) {
                    kvs.putRowBatch(outputTableName, rowBuffer);
                    rowBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/flatMap " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }

        });
    }

    private static void registerPostRDDFold() {
        post("/rdd/fold", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            String zeroElement = URLDecoder.decode(req.queryParams("zeroElement"), "UTF-8");
            TwoStringsToString lambda = (TwoStringsToString) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                String accumulatedValue = zeroElement;

                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);
                while (rows.hasNext()) {
                    Row row = rows.next();
                    String value = row.get("value");
                    accumulatedValue = lambda.op(accumulatedValue, value);
                }
                kvs.put(outputTableName, Hasher.hash(Identifier.genIdentifier()), "value", accumulatedValue);

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/fold " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }

        });
    }

    private static void registerPostRDDFlatMap() {
        post("/rdd/flatmap", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            StringToIterable lambda = (StringToIterable) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<Row> rowBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String value = row.get("value");
                    Iterable<String> newValues = lambda.op(value);

                    if (newValues != null) {
                        for (String newValue : newValues) {
                            Row newRow = new Row(Hasher.hash(Identifier.genIdentifier()));
                            newRow.put("value", newValue);
                            rowBuffer.add(newRow);

                            if (rowBuffer.size() == 100) {
                                kvs.putRowBatch(outputTableName, rowBuffer);
                                rowBuffer.clear();
                            }
                        }
                    }
                }

                if (rowBuffer.size() > 0) {
                    kvs.putRowBatch(outputTableName, rowBuffer);
                    rowBuffer.clear();
                }
                logger.info("Finished a flatmap on a flameworker");
                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/flatMap " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostRDDFlatMapToPair() {
        post("/rdd/flatMapToPair", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            StringToPairIterable lambda = (StringToPairIterable) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<ColInsert> colInsertBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String value = row.get("value");
                    Iterable<FlamePair> newValues = lambda.op(value);

                    if (newValues != null) {
                        for (FlamePair flamePair : newValues) {
                            // kvs.put(outputTableName, flamePair._1(), Identifier.genIdentifier(),
                            // flamePair._2());
                            ColInsert newColInsert = new ColInsert(flamePair._1(), Identifier.genIdentifier(),
                                    flamePair._2().getBytes());
                            colInsertBuffer.add(newColInsert);

                            if (colInsertBuffer.size() > Worker.batchSize) {
                                kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                                colInsertBuffer.clear();
                            }
                        }
                    }
                }

                if (colInsertBuffer.size() > 0) {
                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                    colInsertBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/flatMapToPair " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostRDDMapToPair() {
        post("/rdd/mapToPair", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            StringToPair lambda = (StringToPair) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<ColInsert> colInsertBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String value = row.get("value");
                    FlamePair pair = lambda.op(value);

                    if (pair != null) {
                        // kvs.put(outputTableName, pair._1(), row.key(), pair._2());
                        ColInsert newColInsert = new ColInsert(pair._1(), row.key(), pair._2().getBytes());
                        colInsertBuffer.add(newColInsert);

                        if (colInsertBuffer.size() > Worker.batchSize) {
                            kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                            colInsertBuffer.clear();
                        }
                    }
                }

                if (colInsertBuffer.size() > 0) {
                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                    colInsertBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/mapToPair " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostPairRDDFoldByKey() {
        post("/pairrdd/foldByKey", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            String zeroElement = URLDecoder.decode(req.queryParams("zeroElement"), "UTF-8");
            TwoStringsToString lambda = (TwoStringsToString) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<Row> rowBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String accumulator = zeroElement;

                    for (String column : row.columns()) {
                        accumulator = lambda.op(accumulator, row.get(column));
                    }

                    // kvs.put(outputTableName, row.key(), "accumulatedValue", accumulator);
                    Row newRow = new Row(row.key());
                    newRow.put("accumulatedValue", accumulator);
                    rowBuffer.add(newRow);

                    if (rowBuffer.size() == Worker.batchSize) {
                        kvs.putRowBatch(outputTableName, rowBuffer);
                        rowBuffer.clear();
                    }
                }

                if (rowBuffer.size() > 0) {
                    kvs.putRowBatch(outputTableName, rowBuffer);
                    rowBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive, zeroElement);
                logger.error("Encountered an error during /pairrdd/foldByKey " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostPairRDDFlatMap() {
        post("/pairrdd/flatMap", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            PairToStringIterable lambda = (PairToStringIterable) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<Row> rowBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    for (String column : row.columns()) {
                        FlamePair pair = new FlamePair(row.key(), row.get(column));
                        Iterable<String> result = lambda.op(pair);

                        if (result != null) {
                            for (String aValue : result) {
                                // kvs.put(outputTableName, Hasher.hash(Identifier.genIdentifier()), "value",
                                // aValue);
                                Row newRow = new Row(Hasher.hash(Identifier.genIdentifier()));
                                newRow.put("value", aValue);
                                rowBuffer.add(newRow);

                                if (rowBuffer.size() == Worker.batchSize) {
                                    kvs.putRowBatch(outputTableName, rowBuffer);
                                    rowBuffer.clear();
                                }
                            }
                        }
                    }
                }

                if (rowBuffer.size() > 0) {
                    kvs.putRowBatch(outputTableName, rowBuffer);
                    rowBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /pairrdd/flatMap " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostPairRDDFlatMapToPair() {
        post("/pairrdd/flatMapToPair", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            PairToPairIterable lambda = (PairToPairIterable) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<ColInsert> colInsertBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    for (String column : row.columns()) {
                        FlamePair pair = new FlamePair(row.key(), row.get(column));
                        Iterable<FlamePair> result = lambda.op(pair);

                        if (result != null) {
                            for (FlamePair aPair : result) {
                                // kvs.put(outputTableName, aPair._1(), Identifier.genIdentifier(), aPair._2());
                                ColInsert newColInsert = new ColInsert(aPair._1(), Identifier.genIdentifier(),
                                        aPair._2().getBytes());
                                colInsertBuffer.add(newColInsert);

                                if (colInsertBuffer.size() > Worker.batchSize) {
                                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                                    colInsertBuffer.clear();
                                }
                            }
                        }
                    }
                }

                if (colInsertBuffer.size() > 0) {
                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                    colInsertBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /pairrdd/flatMapToPair " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostPairRDDmapToPair() {
        post("/pairrdd/mapToPair", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            PairToPair lambda = (PairToPair) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<ColInsert> colInsertBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    for (String column : row.columns()) {
                        FlamePair pair = new FlamePair(row.key(), row.get(column));
                        FlamePair result = lambda.op(pair);

                        if (result != null) {
                            ColInsert newColInsert = new ColInsert(result._1(), "value",
                                    result._2().getBytes());
                            colInsertBuffer.add(newColInsert);

                            if (colInsertBuffer.size() > Worker.batchSize) {
                                kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                                colInsertBuffer.clear();
                            }
                        }
                    }
                }

                if (colInsertBuffer.size() > 0) {
                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                    colInsertBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /pairrdd/mapToPair " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostPairRDDJoin() {
        post("/pairrdd/join", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String otherTableName = req.queryParams("otherTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rowsInput = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<ColInsert> colInsertBuffer = new ArrayList<>();

                while (rowsInput.hasNext()) {
                    Row rowInput = rowsInput.next();

                    Row rowOther = kvs.getRow(otherTableName, rowInput.key());
                    if (rowOther != null) {
                        Set<String> inputColumnNames = rowInput.columns();
                        Set<String> otherColumnNames = rowOther.columns();

                        for (String inputColumnName : inputColumnNames) {
                            for (String otherColumnName : otherColumnNames) {
                                String inputColumn = rowInput.get(inputColumnName);
                                String otherColumn = rowOther.get(otherColumnName);

                                String newColumnName = Hasher.hash(inputColumnName) + "_"
                                        + Hasher.hash(otherColumnName);
                                String newColumnValue = inputColumn + "," + otherColumn;

                                // kvs.put(outputTableName, rowInput.key(), newColumnName, newColumnValue);
                                ColInsert newColInsert = new ColInsert(rowInput.key(), newColumnName,
                                        newColumnValue.getBytes());
                                colInsertBuffer.add(newColInsert);

                                if (colInsertBuffer.size() > Worker.batchSize) {
                                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                                    colInsertBuffer.clear();
                                }
                            }
                        }
                    }
                }

                if (colInsertBuffer.size() > 0) {
                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                    colInsertBuffer.clear();
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s, %s)", inputTableName, otherTableName, outputTableName,
                        fromKey, toKeyExclusive);
                logger.error("Encountered an error during /pairrdd/join " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostRDDIntersectTo() {
        post("/rdd/intersect", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String otherTableName = req.queryParams("otherTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> table1 = kvs.scan(inputTableName);
                Iterator<Row> table2 = kvs.scan(otherTableName);

                Set<String> table1Values = new HashSet<>();
                while (table1.hasNext()) {
                    Row row = table1.next();
                    table1Values.add(row.get("value"));
                }

                Set<String> table2Values = new HashSet<>();
                while (table2.hasNext()) {
                    Row row = table2.next();
                    table2Values.add(row.get("value"));
                }

                table1Values.retainAll(table2Values);

                for (String intersectValue : table1Values) {
                    kvs.put(outputTableName, Hasher.hash(intersectValue), "value", intersectValue);
                }

            } catch (Exception e) {
                logger.error("Encountered an exception when trying to intersect table: " + inputTableName
                        + " to table: " + outputTableName);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }

            res.status(200, "OK");
            return null;
        });
    }

    private static void registerPostRDDSample() {
        post("/rdd/sample", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            double f = Double.parseDouble(req.queryParams("sample"));
            KVSClient kvs = new KVSClient(kvsCoordinator);

            Random r = new Random();

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);
                while (rows.hasNext()) {
                    double p = r.nextDouble(0, 1.0);
                    Row row = rows.next();
                    if (p <= f) {
                        kvs.put(outputTableName, row.key(), "value", row.get("value"));
                    }
                }

            } catch (Exception e) {
                logger.error("Ecnountered an error during sampling");
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }

            res.status(200, "OK");
            return null;
        });
    }

    private static void registerPostRDDGroupBy() {
        post("/rdd/groupBy", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");

            StringToString lambda = (StringToString) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName);
                Map<String, String> groupBy = new HashMap<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String columnValue = row.get("value");
                    String augmentedValue = lambda.op(columnValue);

                    if (groupBy.containsKey(augmentedValue)) {
                        String newValue = groupBy.get(augmentedValue) + "," + columnValue;
                        groupBy.put(augmentedValue, newValue);
                    } else {
                        groupBy.put(augmentedValue, columnValue);
                    }
                }

                logger.info(groupBy.toString());

                for (Map.Entry<String, String> entry : groupBy.entrySet()) {
                    kvs.put(outputTableName, entry.getKey(), Hasher.hash(entry.getValue()), entry.getValue());
                }

            } catch (Exception e) {
                logger.error("Encountered an error during groupBy");
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }

            res.status(200, "OK");
            return null;
        });
    }

    private static void registerPostRDDFilter() {
        post("/rdd/filter", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            StringToBoolean lambda = (StringToBoolean) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String value = row.get("value");

                    if (lambda.op(value)) {
                        kvs.put(outputTableName, row.key(), "value", value);
                    }
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/filter " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostPairRDDFilter() {
        post("/pairrdd/filter", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            StringToBoolean lambda = (StringToBoolean) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                while (rows.hasNext()) {
                    Row row = rows.next();
                    for (String column : row.columns()) {
                        String value = row.get(column);

                        if (lambda.op(value)) {
                            kvs.put(outputTableName, row.key(), column, value);
                        }
                    }
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /pairrdd/filter " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostRDDMapPartitions() {
        post("/rdd/mapPartitions", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            IteratorToIterator lambda = (IteratorToIterator) Serializer.byteArrayToObject(req.bodyAsBytes(), myJAR);
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);
                Iterator<String> newIterator = lambda.op(new Iterator<String>() {
                    public boolean hasNext() {
                        return rows.hasNext();
                    }

                    public String next() {
                        return rows.next().get("value");
                    }
                });

                while (newIterator.hasNext()) {
                    String newValue = newIterator.next();
                    kvs.put(outputTableName, Hasher.hash(Identifier.genIdentifier()), "value", newValue);
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/mapPartitions " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }

    private static void registerPostPairRDDCogroup() {
        post("/pairrdd/cogroup", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String otherTableName = req.queryParams("otherTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");
            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);
                Map<String, String> rowsMap = new HashMap<>();
                while (rows.hasNext()) {
                    Row row = rows.next();

                    StringBuilder sb = new StringBuilder();
                    List<String> columnNames = List.copyOf(row.columns());
                    sb.append("[");
                    if (columnNames.size() > 0) {
                        String columnValue = row.get(columnNames.get(0));
                        sb.append(columnValue);
                    }
                    for (int i = 1; i < columnNames.size(); i++) {
                        String columnValue = row.get(columnNames.get(i));
                        sb.append(",");
                        sb.append(columnValue);
                    }
                    sb.append("]");

                    rowsMap.put(row.key(), sb.toString());
                }

                Iterator<Row> otherRows = kvs.scan(otherTableName, fromKey, toKeyExclusive);
                Map<String, String> otherRowsMap = new HashMap<>();
                while (otherRows.hasNext()) {
                    Row otherRow = otherRows.next();

                    StringBuilder sb = new StringBuilder();
                    List<String> columnNames = List.copyOf(otherRow.columns());
                    sb.append("[");
                    if (columnNames.size() > 0) {
                        String columnValue = otherRow.get(columnNames.get(0));
                        sb.append(columnValue);
                    }
                    for (int i = 1; i < columnNames.size(); i++) {
                        String columnValue = otherRow.get(columnNames.get(i));
                        sb.append(",");
                        sb.append(columnValue);
                    }
                    sb.append("]");

                    otherRowsMap.put(otherRow.key(), sb.toString());
                }

                for (String key : rowsMap.keySet()) {
                    String newValue;
                    if (otherRowsMap.containsKey(key)) {
                        newValue = rowsMap.get(key) + "," + otherRowsMap.get(key);
                    } else {
                        newValue = rowsMap.get(key) + ",[]";
                    }
                    kvs.put(outputTableName, key, Identifier.genIdentifier(), newValue);
                }

                for (String key : otherRowsMap.keySet()) {
                    if (!rowsMap.containsKey(key)) {
                        String newValue = "[]," + otherRowsMap.get(key);
                        kvs.put(outputTableName, key, Identifier.genIdentifier(), newValue);
                    }
                }

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s, %s)", inputTableName, otherTableName, outputTableName,
                        fromKey, toKeyExclusive);
                logger.error("Encountered an error during /pairrdd/join " + inputs);
                e.printStackTrace();
                res.status(500, "Internal Server Error");
                return null;
            }
        });

    }

    private static void registerPostRDDInvert() {
        post("/rdd/invert", (req, res) -> {
            String inputTableName = req.queryParams("inputTableName");
            String outputTableName = req.queryParams("outputTableName");
            String kvsCoordinator = req.queryParams("kvsCoordinator");
            String fromKey = req.queryParams("fromKey");
            String toKeyExclusive = req.queryParams("toKeyExclusive");

            KVSClient kvs = new KVSClient(kvsCoordinator);

            try {
                Iterator<Row> rows = kvs.scan(inputTableName, fromKey, toKeyExclusive);

                List<ColInsert> colInsertBuffer = new ArrayList<>();

                while (rows.hasNext()) {
                    Row row = rows.next();
                    String originalKey = row.key();

                    for (String columnName : row.columns()) {
                        String columnValue = row.get(columnName);
                        String newRowKey = columnName;
                        String newColumnName = originalKey;
                        String newColumnValue = columnValue;
                        ColInsert newColInsert = new ColInsert(newRowKey, newColumnName, newColumnValue.getBytes());

                        colInsertBuffer.add(newColInsert);

                        if (colInsertBuffer.size() >= Worker.batchSize) {
                            kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                            colInsertBuffer.clear();
                        }
                    }
                }

                if (!colInsertBuffer.isEmpty()) {
                    kvs.putColInsertBatch(outputTableName, colInsertBuffer);
                    colInsertBuffer.clear();
                }

                logger.info("Inversion completed from table '" + inputTableName + "' to '" + outputTableName + "'.");

                res.status(200, "OK");
                return null;
            } catch (Exception e) {
                String inputs = String.format("(%s, %s, %s, %s)", inputTableName, outputTableName, fromKey,
                        toKeyExclusive);
                logger.error("Encountered an error during /rdd/invert " + inputs, e);
                res.status(500, "Internal Server Error");
                return null;
            }
        });
    }
}
