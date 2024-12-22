package cis5550.kvs;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class Tables {
    private Tables() {}
    
    public static sealed interface PutColumnResult {
        public record Ok() implements PutColumnResult {}
        public record OtherError(Exception e) implements PutColumnResult {}
    }
    public static PutColumnResult putColumn(String tableID, String rowID, String columnID, byte[] columnValue,
            String storageDirectory, Map<String, Map<String, Row>> memory) {
        
        GetRowResult result = Tables.getRow(tableID, rowID, storageDirectory, memory);
        Row row = null;
        if (result instanceof GetRowResult.Ok ok) {
            row = ok.row();
        } else if (result instanceof GetRowResult.RowDoesNotExist) {
            row = new Row(rowID);
        } else if (result instanceof GetRowResult.OtherError otherError) {
            return new PutColumnResult.OtherError(otherError.e());
        }
        
        row.put(columnID, columnValue);
        
        // Write the column to disk.
        if (tableID.startsWith("pt-")) {
            try {
                PtTables.putRow(tableID, row, storageDirectory);
                return new PutColumnResult.Ok();
            } catch (IOException e) {
                return new PutColumnResult.OtherError(e);
            }
        // Write the column to memory.
        } else {
            memory.computeIfAbsent(tableID, k -> new ConcurrentHashMap<>())
                .putIfAbsent(rowID, row);
            return new PutColumnResult.Ok();
        }
    }
    
    public static sealed interface GetColumnResult {
        public record Ok(byte[] columnValue) implements GetColumnResult {}
        public record ValueDoesNotExist() implements GetColumnResult {}
        public record OtherError(Exception e) implements GetColumnResult {}
    }
    public static GetColumnResult getColumn(String tableID, String rowID, String columnID,
            String storageDirectory, Map<String, Map<String, Row>> memory) {
        
        GetRowResult result = Tables.getRow(tableID, rowID, storageDirectory, memory);
        if (result instanceof GetRowResult.Ok ok) {
            Row row = ok.row();
            byte[] columnData = row.getBytes(columnID);
            if (columnData == null) {
                return new GetColumnResult.ValueDoesNotExist();
            }
            return new GetColumnResult.Ok(columnData);
        } else if (result instanceof GetRowResult.RowDoesNotExist) {
            return new GetColumnResult.ValueDoesNotExist();
        } else if (result instanceof GetRowResult.OtherError otherError) {
            return new GetColumnResult.OtherError(otherError.e());
        }
        throw new IllegalStateException(); // Won't happen since the if statement is exhaustive.
    }
    
    public static sealed interface PutRowResult {
    	public record Ok() implements PutRowResult {}
    	public record OtherError(Exception e) implements PutRowResult {}
    }
    public static PutRowResult putRow(String tableID, Row row,
    		String storageDirectory, Map<String, Map<String, Row>> memory) {
    	
    	// Write the row to disk
    	if (tableID.startsWith("pt-")) {
    		try {
    			PtTables.putRow(tableID, row, storageDirectory);
    			return new PutRowResult.Ok();
    		} catch (IOException e) {
    			return new PutRowResult.OtherError(e);
    		}
		// Write the row to memory
    	} else {
    		memory.computeIfAbsent(tableID, k -> new ConcurrentHashMap<>())
    			.putIfAbsent(row.key(), row);
    		return new PutRowResult.Ok();
    	}
    }
    
    public static sealed interface PutRowBatchResult {
        public record Ok() implements PutRowBatchResult {}
        public record OtherError(Exception e, Row row) implements PutRowBatchResult {}
    }
    public static PutRowBatchResult putRowBatch(String tableID, List<Row> rowBatch,
            String storageDirectory, Map<String, Map<String, Row>> memory) {
        
        // Write the row batch to disk
        if (tableID.startsWith("pt-")) {
            for (Row row : rowBatch) {
                try {
                    PtTables.putRow(tableID, row, storageDirectory);
                } catch (IOException e) {
                    return new PutRowBatchResult.OtherError(e, row);
                }
            }
            return new PutRowBatchResult.Ok();
        // Write the row batch to memory
        } else {
            for (Row row : rowBatch) {
                memory.computeIfAbsent(tableID, k -> new ConcurrentHashMap<>())
                    .putIfAbsent(row.key(), row);
            }
            return new PutRowBatchResult.Ok();
        }
    }
    
    public static sealed interface PutColInsertBatchResult {
        public record Ok() implements PutColInsertBatchResult {}
        public record OtherError(Exception e, ColInsert colInsert) implements PutColInsertBatchResult {}
    }
    public static PutColInsertBatchResult putColInsertBatdch(String tableID, List<ColInsert> colInsertBatch,
            String storageDirectory, Map<String, Map<String, Row>> memory) {
        
        for (ColInsert colInsert : colInsertBatch) {
            PutColumnResult result = Tables.putColumn(tableID, colInsert.rowID(), colInsert.colID(), colInsert.colValue(),
                    storageDirectory, memory);
            
            if (result instanceof PutColumnResult.OtherError otherError) {
                return new PutColInsertBatchResult.OtherError(otherError.e(), colInsert);
            }
        }
        
        return new PutColInsertBatchResult.Ok();
    }
    
    public static sealed interface GetRowResult {
        public record Ok(Row row) implements GetRowResult {}
        public record RowDoesNotExist() implements GetRowResult {}
        public record OtherError(Exception e) implements GetRowResult {}
    }
    public static GetRowResult getRow(String tableID, String rowID, String storageDirectory, Map<String, Map<String, Row>> memory) {
        if (tableID.startsWith("pt-")) {
            try {
                Row onDiskRow = PtTables.getRow(tableID, rowID, storageDirectory);
                return new GetRowResult.Ok(onDiskRow);
            } catch (NoSuchFileException e) {
                return new GetRowResult.RowDoesNotExist();
            } catch (Exception e) {
                return new GetRowResult.OtherError(e);
            }
        } else {
            if (memory.containsKey(tableID)) {
                Map<String, Row> requestTable = memory.get(tableID);
                if (requestTable.containsKey(rowID)) {
                    return new GetRowResult.Ok(requestTable.get(rowID));
                }
            }
            return new GetRowResult.RowDoesNotExist();
        }
    }
    
    public static sealed interface StreamTableResult {
        public record Ok(Stream<Row> rowStream) implements StreamTableResult {}
        public record RowDoesNotExist() implements StreamTableResult {}
        public record OtherError(IOException e) implements StreamTableResult {}
    }
    public static StreamTableResult streamTable(String tableID, String startRow, String endRowExclusive, String storageDirectory, Map<String, Map<String, Row>> memory) {
        if (tableID.startsWith("pt-")) {
            try {
                Stream<Row> rowStream = PtTables.streamRows(tableID, startRow, endRowExclusive, storageDirectory);
                return new StreamTableResult.Ok(rowStream);
            } catch (NoSuchFileException e) {
                return new StreamTableResult.RowDoesNotExist();
            } catch (IOException e) {
                return new StreamTableResult.OtherError(e);
            }
        } else {
            Map<String, Row> requestedTable = memory.get(tableID);
            if (requestedTable == null) {
                return new StreamTableResult.RowDoesNotExist();
            }
            Stream<Row> rowStream = requestedTable.values().stream();
            rowStream = rowStream.filter(row -> {
                String rowID = row.key();
                if (startRow == null || rowID.compareTo(startRow) >= 0) {
                    if (endRowExclusive == null || rowID.compareTo(endRowExclusive) < 0) {
                        return true;
                    }
                }
                return false;
            });
            return new StreamTableResult.Ok(rowStream);
        }
    }
    
    public static sealed interface RenameResult {
        public record Ok() implements RenameResult {}
        public record MismatchStorageType() implements RenameResult {}
        public record TableDoesNotExist() implements RenameResult {}
        public record NewTableAlreadyExists() implements RenameResult {}
        public record OtherError(Exception e) implements RenameResult {}
    }
    public static RenameResult rename(String tableID, String newTableID, String storageDirectory, Map<String, Map<String, Row>> memory) {
        // If the tableID is on disk
        if (tableID.startsWith("pt-")) {
            if (!newTableID.startsWith("pt-")) {
                return new RenameResult.MismatchStorageType();
            }
            
            try {
                PtTables.renameTable(tableID, newTableID, storageDirectory);
                return new RenameResult.Ok();
            } catch (NoSuchFileException e) {
                return new RenameResult.TableDoesNotExist();
            } catch (FileAlreadyExistsException e) {
                return new RenameResult.NewTableAlreadyExists();
            } catch (IOException e) {
                return new RenameResult.OtherError(e);
            }
        
        // If the tableID is in memory
        } else {
            // Transfer the in-memory table to disk.
            if (newTableID.startsWith("pt-")) {
                try {
                    if (PtTables.existsTable(newTableID, storageDirectory)) {
                        return new RenameResult.NewTableAlreadyExists();
                    }
                    
                    // Note that putRow will create the directory for the table.
                    var rows = memory.remove(tableID);
                    if (rows == null) {
                        return new RenameResult.TableDoesNotExist();
                    }
                    
                    for (Row row : rows.values()) {
                        PtTables.putRow(newTableID, row, storageDirectory);
                    }
                } catch (IOException e) {
                    return new RenameResult.OtherError(e);
                }
                return new RenameResult.Ok();
            
            // Move the in-memory table to another in-memory table
            } else {
                if (memory.containsKey(newTableID)) {
                    return new RenameResult.NewTableAlreadyExists();
                }
                
                var rows = memory.remove(tableID);
                if (rows == null) {
                    return new RenameResult.TableDoesNotExist();
                }
                
                memory.put(newTableID, rows);
                return new RenameResult.Ok();
            }
        }
    }
    
    public static sealed interface DeleteResult {
        public record Ok() implements DeleteResult {}
        public record TableDoesNotExist() implements DeleteResult {}
        public record OtherError(Exception e) implements DeleteResult {}
    }
    public static DeleteResult delete(String tableID, String storageDirectory, Map<String, Map<String, Row>> memory) {
        // If the tableID is on disk
        if (tableID.startsWith("pt-")) {   
            try {
                PtTables.deleteTable(tableID, storageDirectory);
                return new DeleteResult.Ok();
            } catch (NoSuchFileException e) {
                return new DeleteResult.TableDoesNotExist();
            } catch (IOException e) {
                return new DeleteResult.OtherError(e);
            }
        
        // If the tableID is in memory
        } else {
            var res = memory.remove(tableID);
            if (res == null) {
                return new DeleteResult.TableDoesNotExist();
            }
            return new DeleteResult.Ok();
        }
    }
    
    public static sealed interface ListTablesResult {
        public record Ok(List<String> tableIDs) implements ListTablesResult {}
        public record OtherError(Exception e) implements ListTablesResult {}
    }
    public static ListTablesResult listTables(String storageDirectory, Map<String, Map<String, Row>> memory) {
        List<String> allTableIDs = new ArrayList<>();
        
        // Add all the on-disk tables
        try {
            List<String> onDiskTableIDs = PtTables.listTables(storageDirectory);
            allTableIDs.addAll(onDiskTableIDs);
        } catch (IOException e) {
            return new ListTablesResult.OtherError(e);
        }
        
        // Add all the in-memory tables
        allTableIDs.addAll(memory.keySet());
        return new ListTablesResult.Ok(allTableIDs);
    }
    
    public static sealed interface RowCountResult {
        public record Ok(long rowCount) implements RowCountResult {}
        public record TableDoesNotExist() implements RowCountResult {}
        public record OtherError(Exception e) implements RowCountResult {}
    }
    public static RowCountResult rowCount(String tableID, String storageDirectory, Map<String, Map<String, Row>> memory) {
        // Count rows if the table is on disk.
        if (tableID.startsWith("pt-")) {
            try {
                long rowCount = PtTables.countRows(tableID, storageDirectory);
                return new RowCountResult.Ok(rowCount);
            } catch (NoSuchFileException e) {
                return new RowCountResult.TableDoesNotExist();
            } catch (IOException e) {
                return new RowCountResult.OtherError(e);
            }
        
        // Count rows if the table is in memory.
        } else {
            if (!memory.containsKey(tableID)) {
                return new RowCountResult.TableDoesNotExist();
            }
            return new RowCountResult.Ok(memory.get(tableID).size());
        }
    }
}
