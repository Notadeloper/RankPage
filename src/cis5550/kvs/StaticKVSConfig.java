package cis5550.kvs;

import java.io.Serializable;
import java.util.List;

public record StaticKVSConfig(String coordinator, List<StaticKVSWorkerEntry> workers) implements Serializable {}