package lu.uni.serval.ikora.evolution;

/*-
 * #%L
 * Ikora Evolution
 * %%
 * Copyright (C) 2020 - 2022 University of Luxembourg
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import lu.uni.serval.commons.git.exception.InvalidGitRepositoryException;
import lu.uni.serval.ikora.core.utils.ValueFetcher;
import lu.uni.serval.ikora.evolution.configuration.EvolutionConfiguration;
import lu.uni.serval.ikora.evolution.results.TestRecord;
import lu.uni.serval.ikora.evolution.results.VariableChangeRecord;
import lu.uni.serval.ikora.evolution.export.EvolutionExport;
import lu.uni.serval.ikora.evolution.smells.History;
import lu.uni.serval.ikora.evolution.smells.SmellRecordAccumulator;
import lu.uni.serval.ikora.evolution.results.VersionRecord;
import lu.uni.serval.ikora.evolution.versions.FolderProvider;
import lu.uni.serval.ikora.evolution.versions.VersionProvider;

import lu.uni.serval.ikora.evolution.versions.VersionProviderFactory;
import lu.uni.serval.ikora.smells.SmellConfiguration;
import lu.uni.serval.ikora.smells.SmellDetector;
import lu.uni.serval.ikora.smells.SmellMetric;
import lu.uni.serval.ikora.smells.SmellResults;

import lu.uni.serval.ikora.core.model.*;
import lu.uni.serval.ikora.core.utils.LevenshteinDistance;
import lu.uni.serval.ikora.core.analytics.clones.KeywordCloneDetection;
import lu.uni.serval.ikora.core.analytics.clones.Clones;
import lu.uni.serval.ikora.core.analytics.difference.Difference;
import lu.uni.serval.ikora.core.analytics.difference.Edit;
import lu.uni.serval.ikora.core.analytics.difference.NodeMatcher;

import org.apache.commons.lang3.tuple.Pair;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EvolutionRunner {
    private static final Logger logger = LogManager.getLogger(EvolutionRunner.class);

    private final EvolutionExport exporter;
    private final EvolutionConfiguration configuration;
    private final Set<Pair<? extends SourceNode, ? extends SourceNode>> pairs;
    private final History history;
    private Set<Edit> edits;

    public EvolutionRunner(EvolutionExport exporter, EvolutionConfiguration configuration){
        this.exporter = exporter;
        this.configuration = configuration;
        this.pairs = new HashSet<>();
        this.edits = null;
        this.history = new History();
    }

    public void execute() throws IOException, GitAPIException, InvalidGitRepositoryException {
        try (VersionProvider versionProvider = VersionProviderFactory.fromConfiguration(configuration)) {
            SmellRecordAccumulator previousRecords = null;
            Projects previousVersion = null;

            for(Projects version: versionProvider){
                logger.log(Level.INFO, "Starting analysis for version {}...", version.getVersionId());
                reset(previousVersion, version, versionProvider instanceof FolderProvider);

                computeVersionStatistics(version);
                computeTestStatistics(version);

                previousRecords = computeSmells(version, previousRecords);
                previousVersion = version;

                computeVariableChanges();

                logger.log(Level.INFO, "Analysis for version {} done.", version.getVersionId());
            }
        }
    }

    private void reset(Projects version1, Projects version2, boolean ignoreProjectName){
        setPairs(version1, version2, ignoreProjectName);
        edits = null;
    }

    private void computeVersionStatistics(Projects version) throws IOException {
        if(!this.exporter.contains(EvolutionExport.Statistics.PROJECT)){
            return;
        }

        this.exporter.export(EvolutionExport.Statistics.PROJECT, new VersionRecord(version));
    }

    private SmellRecordAccumulator computeSmells(Projects version, SmellRecordAccumulator previousRecords) throws IOException {
        if(!this.exporter.contains(EvolutionExport.Statistics.SMELL)){
            return new SmellRecordAccumulator();
        }

        SmellRecordAccumulator smellRecordAccumulator = findSmells(version, getEdits(), previousRecords);
        this.exporter.export(EvolutionExport.Statistics.SMELL, smellRecordAccumulator.getRecords());

        return smellRecordAccumulator;
    }

    private void computeTestStatistics(Projects version) throws IOException {
        if(!this.exporter.contains(EvolutionExport.Statistics.TEST)){
            return;
        }

        for(TestCase testCase: version.getTestCases()){
            this.exporter.export(EvolutionExport.Statistics.TEST, new TestRecord(testCase));
        }
    }

    private void computeVariableChanges() throws IOException {
        if(!this.exporter.contains(EvolutionExport.Statistics.VARIABLE_CHANGES)){
            return;
        }

        final Set<Pair<KeywordDefinition, KeywordDefinition>> keywordPairs = this.pairs.stream()
                .filter(p -> p.getLeft() != null && KeywordDefinition.class.isAssignableFrom(p.getLeft().getClass())
                        && p.getRight() != null && KeywordDefinition.class.isAssignableFrom(p.getRight().getClass()))
                .map(p -> (Pair<KeywordDefinition, KeywordDefinition>) p)
                .collect(Collectors.toSet());

        for(Pair<KeywordDefinition, KeywordDefinition> keywordPair: keywordPairs){
            storeValueEdits(keywordPair);
        }
    }

    private SmellRecordAccumulator findSmells(Projects version, Set<Edit> edits, SmellRecordAccumulator previousRecords){
        final SmellConfiguration smellConfiguration = this.configuration.getSmellConfiguration();
        final SmellRecordAccumulator smellRecordAccumulator = new SmellRecordAccumulator();
        final Map<SmellMetric.Type, Set<SourceNode>> previousNodes = previousRecords != null ? previousRecords.getNodes() : null;
        final SmellDetector detector = SmellDetector.all();
        final String versionId = version.getVersionId();
        final Clones<KeywordDefinition> clones = KeywordCloneDetection.findClones(version);

        smellConfiguration.setClones(clones);

        for(Project project: version){
            for(TestCase testCase: project.getTestCases()){
                final SmellResults smellResults = detector.computeMetrics(testCase, smellConfiguration);
                smellRecordAccumulator.addTestCase(versionId, testCase, smellResults, edits, pairs, previousNodes, smellConfiguration, history);
            }
        }

        return smellRecordAccumulator;
    }

    private void setPairs(Projects version1, Projects version2, boolean ignoreProjectName){
        this.pairs.clear();

        if(version1 == null || version2 == null){
            return;
        }

        if(version1.isEmpty() || version2.isEmpty()){
            return;
        }

        pairs.addAll(NodeMatcher.getPairs(version1.getTestCases(), version2.getTestCases(), ignoreProjectName));
        pairs.addAll(NodeMatcher.getPairs(version1.getUserKeywords(), version2.getUserKeywords(), ignoreProjectName));
        pairs.addAll(NodeMatcher.getPairs(version1.getVariableAssignments(), version2.getVariableAssignments(), ignoreProjectName));
    }

    private Set<Edit> getEdits() {
        if(this.edits == null){
            this.edits = pairs.stream()
                    .flatMap(p -> Difference.of(p.getLeft(), p.getRight()).getEdits().stream())
                    .collect(Collectors.toSet());
        }

        return this.edits;
    }

    private void storeValueEdits(Pair<KeywordDefinition, KeywordDefinition> keywordPair) throws IOException {
        if(keywordPair.getLeft() == null || keywordPair.getRight() == null){
            return;
        }

        final List<Pair<Step, Step>> stepPairs = LevenshteinDistance.getMapping(keywordPair.getLeft().getSteps(), keywordPair.getRight().getSteps()).stream()
                .filter(pair -> isLibraryCall(pair.getRight()) && isLibraryCall(pair.getLeft()))
                .collect(Collectors.toList());

        for(Pair<Step, Step> stepPair: stepPairs){
            final NodeList<Argument> beforeArguments = stepPair.getLeft().getArgumentList();
            final NodeList<Argument> afterArguments = stepPair.getRight().getArgumentList();
            final List<Pair<Argument, Argument>> argPairs = LevenshteinDistance.getMapping(beforeArguments, afterArguments);

            for(Pair<Argument, Argument> argPair: argPairs){
                final Set<String> beforeValues = ValueFetcher.getValues(argPair.getLeft());
                final Set<String> afterValues = ValueFetcher.getValues(argPair.getRight());

                if(!beforeValues.isEmpty() && !afterValues.isEmpty() && Collections.disjoint(beforeValues, afterValues)){
                    final VariableChangeRecord changeRecord = new VariableChangeRecord(argPair.getLeft(), beforeValues, argPair.getRight(), afterValues);
                    this.exporter.export(EvolutionExport.Statistics.VARIABLE_CHANGES, changeRecord);
                }
            }
        }
    }

    private boolean isLibraryCall(Step step){
        return step.getKeywordCall().map(
                keywordCall -> keywordCall.getKeyword()
                        .filter(value -> LibraryKeyword.class.isAssignableFrom(value.getClass()))
                        .isPresent()
        ).orElse(false);
    }
}
