package tech.ikora.evolution;

import org.apache.commons.lang3.tuple.Pair;
import tech.ikora.analytics.Difference;
import tech.ikora.analytics.clones.CloneDetection;
import tech.ikora.analytics.clones.Clones;
import tech.ikora.evolution.differences.NodeMatcher;
import tech.ikora.evolution.export.EvolutionExport;
import tech.ikora.evolution.export.Exporter;
import tech.ikora.evolution.results.DifferenceResults;
import tech.ikora.evolution.results.SmellRecordAccumulator;
import tech.ikora.evolution.results.VersionRecord;
import tech.ikora.evolution.versions.FolderProvider;
import tech.ikora.evolution.versions.VersionProvider;
import tech.ikora.model.*;
import tech.ikora.smells.SmellConfiguration;
import tech.ikora.smells.SmellDetector;
import tech.ikora.smells.SmellMetric;
import tech.ikora.smells.SmellResults;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class EvolutionRunner {
    private final VersionProvider versionProvider;
    private final EvolutionExport exporter;
    private final SmellConfiguration smellConfiguration;

    public EvolutionRunner(VersionProvider versionProvider, EvolutionExport exporter, SmellConfiguration smellConfiguration){
        this.versionProvider = versionProvider;
        this.exporter = exporter;
        this.smellConfiguration = smellConfiguration;
    }

    public Map<EvolutionExport.Statistics, Exporter> getExporter() {
        return exporter.getExporters();
    }

    public void execute() throws IOException {
        Projects previousVersion = null;
        SmellRecordAccumulator previousRecords = null;

        for(Projects version: versionProvider){
            computeVersionStatistics(version);

            previousRecords = computeSmells(previousVersion, version, previousRecords == null ? null : previousRecords.getResults());

            previousVersion = version;
        }

        versionProvider.clean();
    }

    private void computeVersionStatistics(Projects version) throws IOException {
        if(!this.exporter.contains(EvolutionExport.Statistics.PROJECT)){
            return;
        }

        this.exporter.export(EvolutionExport.Statistics.PROJECT, new VersionRecord(version));
    }

    private SmellRecordAccumulator computeSmells(Projects previousVersion, Projects version, Map<TestCase, SmellResults> previousResults) throws IOException {
        if(!this.exporter.contains(EvolutionExport.Statistics.SMELL)){
            return new SmellRecordAccumulator();
        }

        boolean ignoreProjectName = versionProvider instanceof FolderProvider;

        DifferenceResults differenceResults = findDifferences(previousVersion, version, ignoreProjectName);
        SmellRecordAccumulator smellRecordAccumulator = findSmells(version, differenceResults, previousResults);
        this.exporter.export(EvolutionExport.Statistics.SMELL, smellRecordAccumulator.getRecords());

        return smellRecordAccumulator;
    }

    private SmellRecordAccumulator findSmells(Projects version, DifferenceResults differenceResults, Map<TestCase, SmellResults> previousResults){
        SmellRecordAccumulator smellRecordAccumulator = new SmellRecordAccumulator();

        final SmellDetector detector = SmellDetector.all();
        final String versionId = version.getVersionId();
        final Clones<UserKeyword> clones = CloneDetection.findClones(version, UserKeyword.class);

        this.smellConfiguration.setClones(clones);

        for(Project project: version){
            for(TestCase testCase: project.getTestCases()){
                final SmellResults smellResults = detector.computeMetrics(testCase, this.smellConfiguration);
                smellRecordAccumulator.addTestCase(versionId, testCase, smellResults, differenceResults, previousResults, this.smellConfiguration);
            }
        }

        return smellRecordAccumulator;
    }

    private DifferenceResults findDifferences(Projects version1, Projects version2, boolean ignoreProjectName){
        DifferenceResults results = new DifferenceResults();

        if(version1 == null || version2 == null){
            return results;
        }

        if(version1.isEmpty() || version2.isEmpty()){
            return results;
        }

        for(Pair<UserKeyword,UserKeyword> keywordPair: NodeMatcher.getPairs(UserKeyword.class, version1, version2, ignoreProjectName)){
            UserKeyword keyword1 = getElement(keywordPair, version1);
            UserKeyword keyword2 = getElement(keywordPair, version2);

            results.update(Difference.of(keyword1, keyword2));
        }

        for(Pair<TestCase,TestCase> testCasePair: NodeMatcher.getPairs(TestCase.class, version1, version2, ignoreProjectName)) {
            TestCase testCase1 = getElement(testCasePair, version1);
            TestCase testCase2 = getElement(testCasePair, version2);

            results.update(Difference.of(testCase1, testCase2));
        }

        for(Pair<VariableAssignment,VariableAssignment> variablePair: NodeMatcher.getPairs(VariableAssignment.class, version1, version2, ignoreProjectName)) {
            VariableAssignment variable1 = getElement(variablePair, version1);
            VariableAssignment variable2 = getElement(variablePair, version2);

            results.update(Difference.of(variable1, variable2));
        }

        return results;
    }

    private <T extends SourceNode> T getElement(Pair<T,T> pair, Projects version){
        if(pair.getRight() != null && version.contains(pair.getRight().getProject())) {
            return pair.getRight();
        }
        else if(pair.getLeft() != null && version.contains(pair.getLeft().getProject())){
            return pair.getLeft();
        }

        return null;
    }
}
