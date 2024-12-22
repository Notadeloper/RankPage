package cis5550.kvs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

public record ColInsert(String rowID, String colID, byte[] colValue) implements Serializable {
    public static byte[] serializeColInsertBatch(List<ColInsert> colInsertBatch) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            oos.writeObject(colInsertBatch);
            byte[] rowBatchData = baos.toByteArray();
            return rowBatchData;
        }
    }

    public static List<ColInsert> deserializeColInsertBatch(byte[] colInsertBatchData) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(colInsertBatchData))) {
            @SuppressWarnings("unchecked")
            List<ColInsert> colInsertBatch = (List<ColInsert>) ois.readObject();
            return colInsertBatch;
        }
    }
}
