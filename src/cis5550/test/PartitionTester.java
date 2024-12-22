package cis5550.test;

import java.util.Vector;

import cis5550.tools.Partitioner;
import cis5550.tools.Partitioner.Partition;

public class PartitionTester {
	public static void main(String[] args) {
		Partitioner p = new Partitioner();
		
		p.addKVSWorker("10.0.0.1:1001", null, null);
		p.addFlameWorker("10.0.0.1:2001");
		
		Vector<Partition> result = p.assignPartitions();
        for (Partition x : result)
            System.out.println(x);
        
        Partitioner p2 = new Partitioner();
        
        p2.addKVSWorker("10.0.0.1:1001", null, "ggggg"); // last kvs worker, id = "xxxxx"
        p2.addKVSWorker("10.0.0.1:1001", "xxxxx", null); // last kvs worker, id = "xxxxx"

        p2.addFlameWorker("10.0.0.1:2001");
        
        Vector<Partition> result2 = p2.assignPartitions();
        for (Partition x : result2) {
        	System.out.println(x);
        }
        
	}
}
