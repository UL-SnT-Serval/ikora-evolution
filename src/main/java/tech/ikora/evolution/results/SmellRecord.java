package tech.ikora.evolution.results;

import tech.ikora.analytics.KeywordStatistics;
import tech.ikora.model.TestCase;
import tech.ikora.smells.SmellMetric;

import java.util.Date;

public class SmellRecord implements CsvRecord {
    private final Date date;
    private final String testCaseName;
    private final int testCaseSize;
    private final int testCaseSequence;
    private final int testCaseLevel;
    private final String smellMetricName;
    private final double smellMetricValue;
    private final int changesCount;

    public SmellRecord(Date date, TestCase testCase, SmellMetric smell, int changesCount) {
        this.date = date;
        this.testCaseName = testCase.toString();
        this.testCaseSize = KeywordStatistics.getSize(testCase);
        this.testCaseSequence = KeywordStatistics.getSequenceSize(testCase);
        this.testCaseLevel = KeywordStatistics.getLevel(testCase);
        this.smellMetricName = smell.getType().name();
        this.smellMetricValue = smell.getValue();
        this.changesCount = changesCount;
    }

    public Date getDate() {
        return date;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public int getTestCaseSize() {
        return testCaseSize;
    }

    public int getTestCaseSequence() {
        return testCaseSequence;
    }

    public int getTestCaseLevel() {
        return testCaseLevel;
    }

    public String getSmellMetricName() {
        return smellMetricName;
    }

    public double getSmellMetricValue() {
        return smellMetricValue;
    }

    public int getChangesCount() {
        return changesCount;
    }

    public Object[] getValues(){
        return new Object[] {
                this.getDate(),
                this.getTestCaseName(),
                String.valueOf(this.getTestCaseSize()),
                String.valueOf(this.getTestCaseSequence()),
                String.valueOf(this.getTestCaseLevel()),
                this.getSmellMetricName(),
                String.valueOf(this.getSmellMetricValue()),
                String.valueOf(this.changesCount)
        };
    }

    public String[] getHeaders() {
        return new String[] {
                "version",
                "test_case_name",
                "test_case_size",
                "test_case_sequence",
                "test_case_level",
                "smell_name",
                "smell_metric",
                "changes"
        };
    }
}
