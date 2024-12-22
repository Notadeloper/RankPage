package cis5550.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;

public class FlameMapPartitions {
    public static void run(FlameContext ctx, String args[]) throws Exception {
        FlameRDD table1 = ctx.parallelize(List.of("apple", "banana", "Orange", "Yellow", "Apple", "Zebra",
                "zero", "boring", "five", "Ten", "System", "Windows", "System", "potatoe", "elephant", "blueberry"));
        
        FlameRDD resultTable = table1.mapPartitions(i -> {
            List<String> elements = new ArrayList<>();
            while (i.hasNext()) {
                elements.add(i.next());
            }
            
            List<String> newElements = new ArrayList<>();
            for (String element : elements) {
                newElements.add(elements.size() + element);
            }
            
            return newElements.iterator();
        });
        
        List<String> result = resultTable.collect();
        
        /*
        boolean correct = true;
        if (resultTable.count() != 8) {
            correct = false;
        }*/
        
        ctx.output(result.toString());
    }
}
