package cis5550.flame;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import cis5550.flame.FlameContextImpl.InvokeOperationResult;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Serializer;

public class FlameRDDImpl implements FlameRDD {
    // private Logger logger = Logger.getLogger(FlameRDDImpl.class);

    private final KVSClient kvs;
    private String correspondingTableName;
    private boolean destroyed = false;

    public FlameRDDImpl(KVSClient kvs, String correspondingTableName) {
        this.kvs = kvs;
        this.correspondingTableName = correspondingTableName;
    }

    private static void requireNotDestroyed(FlameRDDImpl rdd) throws Exception {
        if (rdd.destroyed) {
            throw new Exception("PairRDD " + rdd.correspondingTableName + " is destroyed");
        }
    }

    public int count() throws Exception {
        requireNotDestroyed(this);
        return kvs.count(correspondingTableName);
    }

    public void saveAsTable(String tableNameArg) throws Exception {
        requireNotDestroyed(this);
        kvs.rename(this.correspondingTableName, tableNameArg);
        this.correspondingTableName = tableNameArg;
    }

    public FlameRDD distinct() throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/rdd/distinct",
                null, null);

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the distinct operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public void destroy() throws Exception {
        requireNotDestroyed(this);
        kvs.delete(this.correspondingTableName);
        this.destroyed = true;
    }

    public Vector<String> take(int num) throws Exception {
        requireNotDestroyed(this);
        Iterator<Row> rows = kvs.scan(this.correspondingTableName);
        Vector<String> elements = new Vector<>();

        while (rows.hasNext() && elements.size() < num) {
            Row nextRow = rows.next();
            String element = nextRow.get("value");
            elements.add(element);
        }

        return elements;
    }

    public String fold(String zeroElement, FlamePairRDD.TwoStringsToString lambda) throws Exception {
        requireNotDestroyed(this);
        String zeroElementParam = String.format("zeroElement=%s", URLEncoder.encode(zeroElement, "UTF-8"));

        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName,
                "/rdd/fold", List.of(zeroElementParam), Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            String accumulatedValue = zeroElement;

            Iterator<Row> workerResults = kvs.scan(ok.outputTableName());
            while (workerResults.hasNext()) {
                Row workerResultRow = workerResults.next();
                String workerResult = workerResultRow.get("value");
                accumulatedValue = lambda.op(accumulatedValue, workerResult);
            }

            return accumulatedValue;
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the /pairrdd/foldByKey operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public List<String> collect() throws Exception {
        requireNotDestroyed(this);
        Iterator<Row> tableIterator = kvs.scan(correspondingTableName);

        List<String> values = new ArrayList<>();
        while (tableIterator.hasNext()) {
            Row atRow = tableIterator.next();
            values.add(atRow.get("value"));
        }

        return values;
    }

    public FlameRDD flatMap(StringToIterable lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/rdd/flatmap",
                null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the flatmap operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public FlamePairRDD flatMapToPair(StringToPairIterable lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName,
                "/rdd/flatMapToPair", null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the flatMapToPair operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public FlamePairRDD mapToPair(StringToPair lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/rdd/mapToPair",
                null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the mapToPair operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public FlameRDD intersection(FlameRDD r) throws Exception {
        requireNotDestroyed(this);
        FlameRDDImpl rimpl = (FlameRDDImpl) r;

        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/rdd/intersect",
                List.of("otherTableName=" + rimpl.correspondingTableName), null);

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the last stage of intersection");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    // Extra credit HW6
    public FlameRDD sample(double f) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/rdd/sample",
                List.of("sample=" + f), null);

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing sampling");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    // Extra credit HW6
    public FlamePairRDD groupBy(StringToString lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/rdd/groupBy",
                null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing groupBy");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    // Extra credit HW7
    public FlameRDD filter(StringToBoolean lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/rdd/filter",
                null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing filter");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    // Extra credit HW7
    public FlameRDD mapPartitions(IteratorToIterator lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName,
                "/rdd/mapPartitions", null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing mapPartitions");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public FlameRDD invert(String fromTable) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(fromTable, "/rdd/invert",
                null, null);

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the flatmap operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

}
