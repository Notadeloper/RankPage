package cis5550.kvs;

import java.io.Serializable;

public record StaticKVSWorkerEntry(String address, String id) implements Serializable {}