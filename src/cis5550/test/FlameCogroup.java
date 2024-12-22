package cis5550.test;

import java.util.List;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;

public class FlameCogroup {
    public static void run(FlameContext ctx, String args[]) throws Exception {
        FlamePairRDD table1 = ctx.parallelize(List.of("apple", "banana")).mapToPair(s -> new FlamePair("fruit", s));
        FlamePairRDD table2 = ctx.parallelize(List.of("cherry", "data", "fig")).mapToPair(s -> new FlamePair("fruit", s));
        
        FlamePairRDD result = table1.cogroup(table2);
        
        /*
        boolean correct = true;
        if (resultTable.count() != 8) {
            correct = false;
        }*/
        
        ctx.output(result.collect().toString());
    }
}
