package cis5550.flame;

import cis5550.flame.FlameContextImpl.InvokeOperationResult;
import cis5550.flame.FlameRDD.StringToBoolean;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Serializer;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FlamePairRDDImpl implements FlamePairRDD {
    private final KVSClient kvs;
    private String correspondingTableName;
    private boolean destroyed = false;

    public FlamePairRDDImpl(KVSClient kvs, String correspondingTableName) {
        this.kvs = kvs;
        this.correspondingTableName = correspondingTableName;
    }

    private static void requireNotDestroyed(FlamePairRDDImpl rdd) throws Exception {
        if (rdd.destroyed) {
            throw new Exception("PairRDD " + rdd.correspondingTableName + " is destroyed");
        }
    }

    public int count() throws Exception {
        requireNotDestroyed(this);
        return kvs.count(correspondingTableName);
    }

    public List<FlamePair> collect() throws Exception {
        requireNotDestroyed(this);
        Iterator<Row> tableIterator = kvs.scan(correspondingTableName);

        List<FlamePair> values = new ArrayList<>();
        while (tableIterator.hasNext()) {
            Row atRow = tableIterator.next();

            for (String columnName : atRow.columns()) {
                values.add(new FlamePair(atRow.key(), atRow.get(columnName)));
            }
        }

        return values;
    }

    public FlamePairRDD foldByKey(String zeroElement, TwoStringsToString lambda) throws Exception {
        requireNotDestroyed(this);
        String zeroElementParam = String.format("zeroElement=%s", URLEncoder.encode(zeroElement, "UTF-8"));

        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName,
                "/pairrdd/foldByKey", List.of(zeroElementParam), Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the /pairrdd/foldByKey operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public void saveAsTable(String tableNameArg) throws Exception {
        requireNotDestroyed(this);
        kvs.rename(this.correspondingTableName, tableNameArg);
        this.correspondingTableName = tableNameArg;
    }

    public FlameRDD flatMap(PairToStringIterable lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/pairrdd/flatMap",
                null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the /pairrdd/flatMap operation");
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

    public FlamePairRDD flatMapToPair(PairToPairIterable lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName,
                "/pairrdd/flatMapToPair", null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the /pairrdd/flatMapToPair operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public FlamePairRDD mapToPair(PairToPair lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName,
                "/pairrdd/mapToPair", null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the /pairrdd/mapToPair operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    public FlamePairRDD join(FlamePairRDD other) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/pairrdd/join",
                List.of("otherTableName=" + ((FlamePairRDDImpl) other).correspondingTableName), null);

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the /pairrdd/join operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

    // Extra credit
    public FlamePairRDD cogroup(FlamePairRDD other) throws Exception {
        requireNotDestroyed(this);

        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/pairrdd/cogroup",
                List.of("otherTableName=" + ((FlamePairRDDImpl) other).correspondingTableName), null);

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing the /pairrdd/cogroup operation");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();

    }

    // Extra credit HW7
    public FlamePairRDD filter(StringToBoolean lambda) throws Exception {
        requireNotDestroyed(this);
        InvokeOperationResult status = FlameContextImpl.invokeOperation(this.correspondingTableName, "/pairrdd/filter",
                null, Serializer.objectToByteArray(lambda));

        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlamePairRDDImpl(this.kvs, ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing filter");
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }

        throw new IllegalStateException();
    }

}