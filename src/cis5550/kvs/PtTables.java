package cis5550.kvs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import cis5550.tools.KeyEncoder;

public class PtTables {
    private PtTables() {}
    
    public static Path getRowPath(String tableID, String rowID, String storageDirectory) {
        String rowName = KeyEncoder.encode(rowID);
        if (rowName.length() >= 6) {
            return Path.of(storageDirectory, tableID, "__"+rowName.substring(0, 2), rowName);
        } else {
            return Path.of(storageDirectory, tableID, rowName);
        }
    }
    
    public static void putRow(String tableID, Row row, String storageDirectory) throws IOException {  
        Path rowPath = getRowPath(tableID, row.key(), storageDirectory);
        Files.createDirectories(rowPath.getParent());
        Files.write(rowPath, row.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    public static Row getRow(String tableID, String rowID, String storageDirectory) throws Exception {
        Path rowPath = getRowPath(tableID, rowID, storageDirectory);
        Row onDiskRow = Row.readFrom(Files.newInputStream(rowPath));
        return onDiskRow;
    }
    
    public static long countRows(String tableID, String storageDirectory) throws IOException {
        Path tableLocation = Path.of(storageDirectory, tableID);
        long rowCount = Files.walk(tableLocation).filter(Files::isRegularFile).count();
        return rowCount;
    }
    
    public static Stream<Row> streamRows(String tableID, String startRow, String endRowExclusive, String storageDirectory) throws IOException {
        Path tableLocation = Path.of(storageDirectory, tableID);
        
        Stream<Row> rowStream = Files.walk(tableLocation)
            .filter(Files::isRegularFile)
            .filter(path -> {
                String rowID = path.getFileName().toString();
                if (startRow == null || rowID.compareTo(startRow) >= 0) {
                    if (endRowExclusive == null || rowID.compareTo(endRowExclusive) < 0) {
                        return true;
                    }
                }
                return false;})
            .map(path -> {
                Row row = null;
                try {
                    row = Row.readFrom(Files.newInputStream(path));
                } catch (Exception e) {}
                return Optional.ofNullable(row);})
            .filter(optionalRow -> optionalRow.isPresent())
            .map(optionalRow -> optionalRow.get());
        
        return rowStream;
    }
    
    public static boolean existsTable(String tableID, String storageDirectory) {
        Path tableLocation = Path.of(storageDirectory, tableID);
        return Files.exists(tableLocation);
    }
    
    public static List<String> listTables(String storageDirectory) throws IOException {
        List<String> onDiskTableIDs = Files.list(Path.of(storageDirectory))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("pt-")) // there's other stuff in the directory.
                .toList();
        return onDiskTableIDs;
    }
    
    public static void renameTable(String tableID, String newTableID, String storageDirectory) throws IOException {
        Path tableLocation = Path.of(storageDirectory, tableID);
        Path newTableLocation = Path.of(storageDirectory, newTableID);
        Files.move(tableLocation, newTableLocation);
    }
    
    public static void deleteTable(String tableID, String storageDirectory) throws IOException {
        Path tableLocation = Path.of(storageDirectory, tableID);
        List<Path> rowFiles =  Files.walk(tableLocation)
                .sorted((file1, file2) -> file2.compareTo(file1))
                .toList();
        
        for (Path rowFile : rowFiles) {
            Files.delete(rowFile);
        }
    }
    
}
