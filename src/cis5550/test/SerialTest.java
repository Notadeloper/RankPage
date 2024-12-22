package cis5550.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import cis5550.jobs.Robots;
import cis5550.jobs.Robots.RobotRule;
import cis5550.kvs.Row;

public class SerialTest {
    
    public static void test1() throws Exception {
        Row row1 = new Row("hi");
        row1.put("a", "b");
        Row row2 = new Row("hello");
        row2.put("c", "d");
        
        List<Row> rows = List.of(row1, row2);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(rows);
        
        byte[] data = baos.toByteArray();
        
        
        List<Row> newRows = (List<Row>) new ObjectInputStream(new ByteArrayInputStream(data)).readObject();
        
        System.out.println(newRows);
        
    }
    
    public static void test2() throws Exception {
        String robotsTXT = "User-agent: *\r\n"
                + "Disallow: /search\r\n"
                + "Allow: /search/about\r\n"
                + "Allow: /search/static\r\n"
                + "Allow: /search/howsearchworks\r\n"
                + "Disallow: /sdch\r\n"
                + "Disallow: /groups\r\n"
                + "Disallow: /index.html?";
        
        List<RobotRule> rules = Robots.parseRobotsTXT(robotsTXT, "cis5550-crawler");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        
        oos.writeObject(rules);
        
        byte[] data = baos.toByteArray();
        
        
        List<RobotRule> newRules = (List<RobotRule>) new ObjectInputStream(new ByteArrayInputStream(data)).readObject();
       
        System.out.println(newRules);
    }
    
    public static void main(String[] args) throws Exception {
        test1();
        
    }
       
}
