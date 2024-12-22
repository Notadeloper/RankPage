package cis5550.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;

public class FlatMapBenchmark {
    
    public static void run(FlameContext flameContext, String[] args) {
        List<String> testData = new ArrayList<>();
        
        for (int i = 0; i < 10000; i++) {
            testData.add("" + i);
        }
        
        try {
            long start = Instant.now().toEpochMilli();
            
            FlameRDD table = flameContext.parallelize(testData);
            table.flatMap(s -> {
                int i = Integer.parseInt(s.substring(0, 1));
                List<String> temp = new ArrayList<>();
                for (int j = 0; j < i; j++) {
                    temp.add("" + i);
                }
                return temp;
            });
            
            long end = Instant.now().toEpochMilli();
            flameContext.output("Time taken: " + (end - start));
        } catch (Exception e) {
            e.printStackTrace();
            flameContext.output("Exception: " + e);
        }
    }
    
}
