package cis5550.flame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import cis5550.kvs.KVSClient;
import cis5550.tools.HTTP;
import cis5550.tools.HTTP.Response;
import cis5550.tools.Hasher;
import cis5550.tools.Identifier;
import cis5550.tools.Logger;
import cis5550.tools.Partitioner;
import cis5550.tools.Serializer;
import cis5550.tools.Partitioner.Partition;

public class FlameContextImpl implements FlameContext {
    private static final Logger logger = Logger.getLogger(FlameContextImpl.class);
    
    private final StringBuilder outputs = new StringBuilder();
    
    public FlameContextImpl() {}
    
    public KVSClient getKVS() {
        return Coordinator.kvs;
    }
    
    public sealed interface InvokeOperationResult {
        record Ok(String outputTableName) implements InvokeOperationResult {}
        record WorkerFailed() implements InvokeOperationResult {}
        record OtherError(Exception e) implements InvokeOperationResult {}
    }
    public static InvokeOperationResult invokeOperation(
            String inputTableName,
            String operationName,
            List<String> extraQueryParams,
            byte[] lambda) {
        
        String outputTableName = Identifier.genIdentifier();
        
        KVSClient kvs = Coordinator.kvs;
        
        Partitioner partitioner = new Partitioner();
        try {
            int numWorkers = kvs.numWorkers();
            
            if (numWorkers == 1) {
            	partitioner.addKVSWorker(kvs.getWorkerAddress(0), null, null);
            } else {
            	for (int i = 0; i < numWorkers - 1; i++) {
                    partitioner.addKVSWorker(kvs.getWorkerAddress(i), kvs.getWorkerID(i), kvs.getWorkerID(i+1));
                }
                
                partitioner.addKVSWorker(kvs.getWorkerAddress(numWorkers - 1), null, kvs.getWorkerID(0));
                partitioner.addKVSWorker(kvs.getWorkerAddress(numWorkers - 1), kvs.getWorkerID(numWorkers - 1), null);
            }
        } catch (IOException e) {
            return new InvokeOperationResult.OtherError(e);
        }
        for (String flameWorkerID : Coordinator.getWorkers()) {
            partitioner.addFlameWorker(flameWorkerID);
        }
        
        Vector<Partition> partitions = partitioner.assignPartitions();
        
        List<Thread> requests = new ArrayList<>();
        AtomicBoolean non200 = new AtomicBoolean(false);
        
        for (Partition partition : partitions) {
            Thread requestThread = new Thread(() -> {
                StringBuilder stringURL = new StringBuilder();
                stringURL.append("http://");
                stringURL.append(partition.assignedFlameWorker);
                stringURL.append(operationName);
                stringURL.append("?");
                
                stringURL.append("inputTableName=");
                stringURL.append(inputTableName);
                
                stringURL.append("&outputTableName=");
                stringURL.append(outputTableName);
                
                stringURL.append("&kvsCoordinator=");
                stringURL.append(kvs.getCoordinator());
                
                if (partition.fromKey != null) {
                    stringURL.append("&fromKey=");
                    stringURL.append(partition.fromKey);
                }
                
                if (partition.toKeyExclusive != null) {
                    stringURL.append("&toKeyExclusive=");
                    stringURL.append(partition.toKeyExclusive);
                }
                
                if (extraQueryParams != null) {
                    for (String extraQueryParam : extraQueryParams) {
                        stringURL.append("&");
                        stringURL.append(extraQueryParam);
                    }
                }
                
                try {
                    Response response = HTTP.doRequest("POST", stringURL.toString(), lambda);
                    if (response.statusCode() != 200) {
                        logger.error("A worker returned the status code " + response.statusCode());
                        non200.set(true);
                    }
                    
                } catch (IOException e) {
                    logger.error("A worker encountered the error: " + e);
                    non200.set(true);
                }
            });
            requests.add(requestThread);
            requestThread.start();
        }
        
        for (Thread requestThread : requests) {
            try {
                requestThread.join(); 
            } catch (InterruptedException e) {
                return new InvokeOperationResult.OtherError(e);
            }
        }
        
        if (non200.get()) {
            return new InvokeOperationResult.WorkerFailed();
        } else {
            return new InvokeOperationResult.Ok(outputTableName);
        }
    }
    
    public void output(String s) {
        outputs.append(s);
    }
    
    public String getOutput() {
        return outputs.toString();
    }
    
    public FlameRDD parallelize(List<String> list) throws Exception {
        String outputTableName = Identifier.genIdentifier();
        
        for (String value: list) {
            String rowName = Hasher.hash(Identifier.genIdentifier());
            Coordinator.kvs.put(outputTableName, rowName, "value", value);
        }
        
        return new FlameRDDImpl(Coordinator.kvs, outputTableName);
    }
    
    public FlameRDD fromTable(String tableName, RowToString lambda) throws Exception {
        InvokeOperationResult status = FlameContextImpl.invokeOperation(tableName, "/context/fromTable", null, Serializer.objectToByteArray(lambda));
        
        if (status instanceof InvokeOperationResult.Ok ok) {
            return new FlameRDDImpl(getKVS(), ok.outputTableName());
        } else if (status instanceof InvokeOperationResult.WorkerFailed) {
            throw new Exception("A worker failed while performing fromTable on " + tableName);
        } else if (status instanceof InvokeOperationResult.OtherError e) {
            throw new Exception(e.e());
        }
        
        return null;
    }
}
