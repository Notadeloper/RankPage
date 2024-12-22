package cis5550.jobs;

import java.util.List;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;

public class FlatMapToPairTest {
    public static void run(FlameContext flameContext, String[] args) {
        List<String> testData = List.of("a", "b", "c", "d", "e");
        
        try {
            FlameRDD table = flameContext.parallelize(testData);
            
            FlamePairRDD table2 = table.flatMapToPair(s -> {
                FlamePair pair = new FlamePair(s, s+s);
                FlamePair pair2 = new FlamePair(s, s+s+s);
                return List.of(pair, pair2);
            });
            
            /*
            FlamePairRDD table3 = table2.flatMapToPair(s -> {
                FlamePair pair = new FlamePair(s._1(), s._2() + s._2());
                return List.of(s, pair);
            });
            */
            
            flameContext.output(table2.collect().toString());
            
        } catch (Exception e) {
            e.printStackTrace();
            flameContext.output("Exception: " + e);
        }
        
    }
}
