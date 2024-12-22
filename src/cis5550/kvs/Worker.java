package cis5550.kvs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import cis5550.kvs.Tables.DeleteResult;
import cis5550.kvs.Tables.GetColumnResult;
import cis5550.kvs.Tables.GetRowResult;
import cis5550.kvs.Tables.ListTablesResult;
import cis5550.kvs.Tables.PutColInsertBatchResult;
import cis5550.kvs.Tables.PutColumnResult;
import cis5550.kvs.Tables.PutRowBatchResult;
import cis5550.kvs.Tables.PutRowResult;
import cis5550.kvs.Tables.RenameResult;
import cis5550.kvs.Tables.RowCountResult;
import cis5550.kvs.Tables.StreamTableResult;
import cis5550.tools.HTTP;
import cis5550.tools.Logger;
import cis5550.webserver.Server;

public class Worker extends cis5550.generic.Worker {
    private static final Logger logger = Logger.getLogger(Worker.class);

    private static Map<String, Map<String, Row>> data = new ConcurrentHashMap<>();
    
    private static ConcurrentHashMap<String, String> lockMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        if (args.length != 3) {
            logger.error("Incorrect number of command line arguments");
            System.exit(1);
        }

        Worker.portNumber = Integer.parseInt(args[0]);
        String storageDirectory = args[1];
        Worker.coordinator = args[2];

        Optional<String> workerID = getWorkerID(storageDirectory);
        if (workerID.isEmpty()) {
            logger.error("Failed to get a workerID for this worker. Closing worker");
            return;
        }
        Worker.workerID = workerID.get();

        logger.info(String.format("Starting KVS Worker: workerID: %s\nport: %s\nstorageDirectory: %s\ncoordinator: %s\n",
                Worker.workerID, Worker.portNumber, storageDirectory, Worker.coordinator));
        Server.port(Worker.portNumber);

        Worker.startPingThread();
        (new Thread(() -> workerListThread())).start();
        
        registerRowLock();
        registerRowUnlock();
        
        registerPutColumn(storageDirectory);
        registerGetColumn(storageDirectory);

        registerPutRow(storageDirectory);
        registerPutRowBatch(storageDirectory);
        registerPutColInsertBatch(storageDirectory);
        registerGetRow(storageDirectory);

        registerStreamRows(storageDirectory);
        registerRenameTable(storageDirectory);
        registerDeleteTable(storageDirectory);
        registerListTables(storageDirectory);
        registerRowCount(storageDirectory);

        registerGetTablesUI(storageDirectory);
        registerViewTableUI(storageDirectory);

    }
    
    private static void registerRowLock() {
        Server.put("/lock/:table/:row", (req, res) -> {
            String tableID = req.params("table");
            String rowID = req.params("row");
            
            String lockKey = tableID + ";" + rowID;
            String previous = lockMap.putIfAbsent(lockKey, "Locked");
            if (previous == null) {
                res.status(200, "OK");
            } else {
                res.status(409, "Conflict");
            }
            return null;
        });
    }
    
    private static void registerRowUnlock() {
        Server.put("/unlock/:table/:row", (req, res) -> {
            String tableID = req.params("table");
            String rowID = req.params("row");
            
            String lockKey = tableID + ";" + rowID;
            String previous = lockMap.remove(lockKey);
            if (previous == null) {
                res.status(404, "Not Found");
                return null;
            } else {
                res.status(200, "OK");
            }
            return null;
        });
    }

    private static void workerListThread() {
        String stringURL = String.format("http://%s/workers", coordinator);
        while (true) {
            try {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                HTTP.Response response = HTTP.doRequest("GET", stringURL, null);
                String body = new String(response.body());
                List<String> bodyParts = Arrays.asList(body.split("\n"));

                Map<String, String> workers = new HashMap<>();
                for (String worker : bodyParts.subList(1, bodyParts.size())) {
                    String[] workerParts = worker.split(",");
                    workers.put(workerParts[0], workerParts[1]);
                }

                List<String> workerIDs = new ArrayList<String>(workers.keySet());
                workerIDs.sort((a, b) -> a.compareTo(b));

                logger.info("WorkerIDs: " + workerIDs.toString());

                int posThisWorker = workerIDs.indexOf(Worker.workerID);
                if (posThisWorker == -1) {
                    logger.error("This worker is not registered on the coordinator");
                }
            } catch (IOException e) {
                logger.error("Failed to get the list of workers from the coordinator");
                e.printStackTrace();
            }
        }
    }

    private static void registerPutColumn(String storageDirectory) {
        Server.put("/data/:table/:row/:column", (req, res) -> {
            String tableID = req.params("table");
            String rowID = req.params("row");
            String columnID = req.params("column");
            byte[] columnValue = req.bodyAsBytes();

            PutColumnResult result = Tables.putColumn(tableID, rowID, columnID, columnValue, storageDirectory, data);
            if (result instanceof PutColumnResult.Ok) {
                res.status(200, "OK");
                res.body("OK");
            } else if (result instanceof PutColumnResult.OtherError otherError) {
                logger.error("Encountered an error when trying to set " + columnID + " in " + rowID + " in " + tableID);
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    private static void registerGetColumn(String storageDirectory) {
        Server.get("/data/:table/:row/:column", (req, res) -> {
            String tableID = req.params("table");
            String rowID = req.params("row");
            String columnID = req.params("column");

            GetColumnResult result = Tables.getColumn(tableID, rowID, columnID, storageDirectory, data);
            if (result instanceof GetColumnResult.Ok ok) {
                res.status(200, "OK");
                res.bodyAsBytes(ok.columnValue());
            } else if (result instanceof GetColumnResult.ValueDoesNotExist) {
                res.status(404, "Not Found");
            } else if (result instanceof GetColumnResult.OtherError otherError) {
                logger.error("Encountered an error when trying to get " + columnID + " in " + rowID + " in " + tableID);
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    private static void registerPutRow(String storageDirectory) {
        Server.put("/data/:table", (req, res) -> {
            String tableID = req.params("table");
            byte[] rowData = req.bodyAsBytes();
            Row row = Row.readFrom(new ByteArrayInputStream(rowData));

            PutRowResult result = Tables.putRow(tableID, row, storageDirectory, data);
            if (result instanceof PutRowResult.Ok) {
                res.status(200, "OK");
                res.body("OK");
            } else if (result instanceof PutRowResult.OtherError otherError) {
                logger.error("Encountered an error when trying to put the row whose key is " + row.key() + " into "
                        + tableID);
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    public static void registerPutRowBatch(String storageDirectory) {
        Server.put("/batch/:table", (req, res) -> {
            String tableID = req.params("table");
            byte[] rowBatchData = req.bodyAsBytes();

            try {
                List<Row> rows = Row.deserializeRowBatch(rowBatchData);
                PutRowBatchResult result = Tables.putRowBatch(tableID, rows, storageDirectory, data);
                if (result instanceof PutRowBatchResult.Ok) {
                    res.status(200, "OK");
                    res.body("OK");
                } else if (result instanceof PutRowBatchResult.OtherError otherError) {
                    logger.info("Encountered an error while inserting " + otherError.row() + " from a row batch into "
                            + tableID);
                    otherError.e().printStackTrace();
                    res.status(500, "Internal Server Error");
                }
            } catch (Exception e) {
                logger.error("Failed to deserialize the row batch");
                e.printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    public static void registerPutColInsertBatch(String storageDirectory) {
        Server.put("/colbatch/:table", (req, res) -> {
            String tableID = req.params("table");
            byte[] colInsertBatchData = req.bodyAsBytes();

            try {
                List<ColInsert> colInserts = ColInsert.deserializeColInsertBatch(colInsertBatchData);
                PutColInsertBatchResult result = Tables.putColInsertBatdch(tableID, colInserts, storageDirectory, data);
                if (result instanceof PutColInsertBatchResult.Ok) {
                    res.status(200, "OK");
                    res.body("OK");
                } else if (result instanceof PutColInsertBatchResult.OtherError otherError) {
                    logger.info("Encountered an error while inserting " + otherError.colInsert()
                            + " from a colInsert batch into " + tableID);
                    otherError.e().printStackTrace();
                    res.status(500, "Internal Server Error");
                }
            } catch (Exception e) {
                logger.error("Failed to deserialize the colInsert batch");
                e.printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    private static void registerGetRow(String storageDirectory) {
        Server.get("/data/:table/:row", (req, res) -> {
            String tableID = req.params("table");
            String rowID = req.params("row");

            GetRowResult result = Tables.getRow(tableID, rowID, storageDirectory, data);
            if (result instanceof GetRowResult.Ok ok) {
                res.status(200, "OK");
                res.bodyAsBytes(ok.row().toByteArray());
            } else if (result instanceof GetRowResult.RowDoesNotExist) {
                res.status(404, "Not Found");
            } else if (result instanceof GetRowResult.OtherError otherError) {
                logger.error("Failed to count the row: " + rowID + " in table: " + tableID);
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            res.type("text/plain");
            return null;
        });
    }

    private static void registerStreamRows(String storageDirectory) {
        Server.get("/data/:table", (req, res) -> {
            String tableID = req.params("table");
            String startRowString = req.queryParams("startRow");
            String endRowExclusiveString = req.queryParams("endRowExclusive");

            res.type("text/plain");
            StreamTableResult result = Tables.streamTable(tableID, startRowString, endRowExclusiveString,
                    storageDirectory, data);
            if (result instanceof StreamTableResult.Ok ok) {
                try {
                    Stream<Row> rows = ok.rowStream();
                    rows.forEach(row -> {
                        try {
                            res.write(row.toByteArray());
                            res.write("\n".getBytes());
                        } catch (Exception e) {
                            logger.error("Error while writing the rows to the client for table: " + tableID + e);
                        }
                    });
                    res.write("\n".getBytes());
                } catch (Exception e) {
                    logger.error("Error while trying to write to the client");
                    e.printStackTrace();
                    return null;
                }
            } else if (result instanceof StreamTableResult.RowDoesNotExist) {
                res.status(404, "Not Found");
            } else if (result instanceof StreamTableResult.OtherError otherError) {
                logger.error("Error while trying to get a stream of the rows in table: " + tableID);
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }
            return null;
        });
    }

    private static void registerRenameTable(String storageDirectory) {
        Server.put("/rename/:table", (req, res) -> {
            String tableID = req.params("table");
            String newTableID = req.body();

            RenameResult result = Tables.rename(tableID, newTableID, storageDirectory, data);
            if (result instanceof RenameResult.Ok) {
                res.status(200, "OK");
                res.body("OK");
            } else if (result instanceof RenameResult.MismatchStorageType) {
                res.status(400, "Bad Request");
            } else if (result instanceof RenameResult.TableDoesNotExist) {
                res.status(404, "Not Found");
            } else if (result instanceof RenameResult.NewTableAlreadyExists) {
                res.status(409, "Conflict");
            } else if (result instanceof RenameResult.OtherError error) {
                logger.error("Encountered an error when trying to rename " + tableID + " to " + newTableID);
                error.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    private static void registerDeleteTable(String storageDirectory) {
        Server.put("/delete/:table", (req, res) -> {
            String tableID = req.params("table");

            DeleteResult result = Tables.delete(tableID, storageDirectory, data);
            if (result instanceof DeleteResult.Ok) {
                res.status(200, "OK");
                res.body("OK");
            } else if (result instanceof DeleteResult.TableDoesNotExist) {
                res.status(404, "Not Found");
            } else if (result instanceof DeleteResult.OtherError error) {
                logger.error("Encountered an error when trying to delete " + tableID);
                error.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    private static void registerListTables(String storageDirectory) {
        Server.get("/tables", (req, res) -> {
            ListTablesResult result = Tables.listTables(storageDirectory, data);

            if (result instanceof ListTablesResult.Ok okResult) {
                StringBuilder sb = new StringBuilder();
                for (String tableID : okResult.tableIDs()) {
                    sb.append(tableID);
                    sb.append("\n");
                }
                res.status(200, "OK");
                res.body(sb.toString());
            } else if (result instanceof ListTablesResult.OtherError otherError) {
                logger.error("Failed to list the tables for this worker ");
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            res.type("text/plain");
            return null;
        });
    }

    private static void registerRowCount(String storageDirectory) {
        Server.get("/count/:table", (req, res) -> {
            String tableID = req.params("table");

            RowCountResult result = Tables.rowCount(tableID, storageDirectory, data);
            if (result instanceof RowCountResult.Ok ok) {
                res.status(200, "OK");
                res.body("" + ok.rowCount());
            } else if (result instanceof RowCountResult.TableDoesNotExist) {
                res.status(404, "Not Found");
            } else if (result instanceof RowCountResult.OtherError otherError) {
                logger.error("Failed to count the rows in table: " + tableID);
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            res.type("text/plain");
            return null;
        });
    }

    private static void registerGetTablesUI(String storageDirectory) {
        String tablesUITemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Tables UI</title>
                </head>
                <body>
                    <table border="1">
                        <thead>
                            <tr>
                                <th>Table Name</th>
                                <th>Hyperlink</th>
                                <th>Number of Keys</th>
                            </tr>
                        </thead>
                        <tbody>
                            %s
                        </tbody>
                    </table>
                </body>
                </html>
                """;

        Server.get("/", (req, res) -> {
            res.type("text/html");

            ListTablesResult result = Tables.listTables(storageDirectory, data);
            if (result instanceof ListTablesResult.Ok ok) {
                StringBuilder sb = new StringBuilder();
                List<String> tableIDs = ok.tableIDs();

                for (String tableID : tableIDs) {
                    RowCountResult result2 = Tables.rowCount(tableID, storageDirectory, data);
                    if (result2 instanceof RowCountResult.Ok ok2) {
                        long rowCount = ok2.rowCount();
                        String link = String.format("/view/%s", tableID);

                        sb.append("<tr>");
                        sb.append("<td>");
                        sb.append(tableID);
                        sb.append("</td>");
                        sb.append("<td>");
                        sb.append("<a href=\"");
                        sb.append(link);
                        sb.append("\">");
                        sb.append(link);
                        sb.append("</a>");
                        sb.append("</td>");
                        sb.append("<td>");
                        sb.append(rowCount);
                        sb.append("</td>");
                        sb.append("</tr>");
                    }
                }
                res.body(String.format(tablesUITemplate, sb.toString()));

            } else if (result instanceof ListTablesResult.OtherError otherError) {
                logger.error("Encountered error while trying to list table");
                otherError.e().printStackTrace();
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    private static void registerViewTableUI(String storageDirectory) {
        String viewTableUITemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>View %s</title>
                </head>
                <body>
                    <table border="1">
                        <caption> %s </caption>
                        <thead>
                            <tr>
                                <th>Row Key</th>
                                %s
                            </tr>
                        </thead>
                        <tbody>
                            %s
                        </tbody>
                    </table>
                    <p>%s</p>
                </body>
                </html>
                """;

        Server.get("/view/:table", (req, res) -> {
            String tableID = req.params("table");
            String fromRow = req.queryParams("fromRow");

            res.type("text/html");

            StreamTableResult result = Tables.streamTable(tableID, null, null, storageDirectory, data);
            if (result instanceof StreamTableResult.Ok ok) {
                Stream<Row> rows = ok.rowStream();
                if (fromRow != null) {
                    rows = rows.filter(row -> row.key().compareTo(fromRow) >= 0);
                }

                List<Row> sortedRows = rows.sorted((row1, row2) -> row1.key().compareTo(row2.key())).limit(11).toList();
                boolean needsNextRow = false;
                String nextFromRow = null;
                if (sortedRows.size() == 11) {
                    // Basically, if we have over ten entries, we need another row
                    needsNextRow = true;
                    nextFromRow = sortedRows.get(10).key();
                    sortedRows = sortedRows.subList(0, 10);
                }

                // Note that we only want columns for the ten rows we are going to display.
                SortedSet<String> sortedColumnIDs = new TreeSet<>();
                for (Row row : sortedRows) {
                    sortedColumnIDs.addAll(row.columns());
                }

                StringBuilder columnHeaders = new StringBuilder();
                for (String columnID : sortedColumnIDs) {
                    columnHeaders.append("<th>");
                    columnHeaders.append(columnID);
                    columnHeaders.append("</th>");
                }

                StringBuilder tableBody = new StringBuilder();
                for (Row row : sortedRows) {
                    tableBody.append("<tr>");
                    tableBody.append("<td>");
                    tableBody.append(row.key());
                    tableBody.append("</td>");
                    for (String columnID : sortedColumnIDs) {
                        tableBody.append("<td>");
                        if (row.columns().contains(columnID)) {
                            String columnData = row.get(columnID);
                            tableBody.append(columnData);
                        }
                        tableBody.append("</td>");
                    }
                    tableBody.append("</tr>");
                }

                String nextLink = "";
                if (needsNextRow) {
                    nextLink = String.format("<a href=\"/view/%s?fromRow=%s\">Next</a>", tableID, nextFromRow);
                }

                res.body(String.format(viewTableUITemplate, tableID, tableID, columnHeaders.toString(),
                        tableBody.toString(), nextLink));
            } else if (result instanceof StreamTableResult.RowDoesNotExist) {
                res.status(404, "Not Found");
            } else if (result instanceof StreamTableResult.OtherError) {
                res.status(500, "Internal Server Error");
            }

            return null;
        });
    }

    private static Optional<String> getWorkerID(String storageDirectory) {
        try {
            Path idFilePath = Path.of(storageDirectory, "id");
            Optional<String> onDisk = lookupWorkerID(idFilePath);
            if (onDisk.isEmpty()) {
                String newWorkerID = generateWorkerID();
                Files.createDirectories(Path.of(storageDirectory)); // Needed when the directory itself doesn't exist.
                Files.writeString(idFilePath, newWorkerID, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                return Optional.of(newWorkerID);
            } else {
                return onDisk;
            }
        } catch (IOException e) {
            logger.error("Failed to get or write a worker id from disk. Error: " + e);
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private static Optional<String> lookupWorkerID(Path idFilePath) throws IOException {
        if (Files.exists(idFilePath)) {
            String dataOnFile = Files.readString(idFilePath);
            return Optional.of(dataOnFile);
        }

        return Optional.empty();
    }

    private static final String idCharacters = "abcdefghijklmnopqrstuvwxyz";

    private static String generateWorkerID() {
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            id.append(idCharacters.charAt(threadLocalRandom.nextInt(26)));
        }
        return id.toString();
    }
}
