package cis5550.test;

import java.util.List;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;

public class FlameFilter {
    public static void run(FlameContext ctx, String args[]) throws Exception {
        FlameRDD table1 = ctx.parallelize(List.of("apple", "banana", "Orange", "Yellow", "Apple", "Zebra",
                "zero", "boring", "five", "Ten", "System", "Windows", "System"));
        
        FlameRDD resultTable = table1.filter(s -> Character.isUpperCase(s.charAt(0)));
        
        List<String> result = resultTable.collect();
        
        /*
        boolean correct = true;
        if (resultTable.count() != 8) {
            correct = false;
        }*/
        
        ctx.output(result.toString());
    }
}
