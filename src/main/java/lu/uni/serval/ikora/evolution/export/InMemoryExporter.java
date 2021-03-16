package lu.uni.serval.ikora.evolution.export;

import lu.uni.serval.ikora.evolution.results.Record;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InMemoryExporter implements Exporter {
    private final String absolutePath;
    private final boolean isHashNames;
    private final List<Record> records = new ArrayList<>();

    public InMemoryExporter(String absolutePath, boolean isHashNames) {
        this.isHashNames = isHashNames;
        this.absolutePath = absolutePath;
    }

    @Override
    public void addRecord(Record record) throws IOException {
        this.records.add(record);
    }

    @Override
    public void addRecords(List<Record> records) throws IOException {
        this.records.addAll(records);
    }

    @Override
    public void close() throws IOException {
        //nothing to do;
    }

    public List<Record> getRecords() {
        return records;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public boolean isHashNames() {
        return isHashNames;
    }
}
