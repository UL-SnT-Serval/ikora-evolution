package tech.ikora.evolution;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import tech.ikora.evolution.configuration.*;
import tech.ikora.evolution.export.EvolutionExport;
import tech.ikora.evolution.versions.GitProvider;
import tech.ikora.evolution.versions.VersionProvider;
import tech.ikora.gitloader.git.GitCommit;
import tech.ikora.gitloader.git.GitUtils;
import tech.ikora.gitloader.git.LocalRepository;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EvolutionRunnerFactory {
    public static EvolutionRunner fromConfiguration(EvolutionConfiguration configuration) throws GitAPIException, IOException {
        EvolutionExport exporter = createExporter(configuration.getOutputConfiguration());
        VersionProvider provider = createVersionProvider(configuration);

        return new EvolutionRunner(provider, exporter);
    }

    private static VersionProvider createVersionProvider(EvolutionConfiguration configuration) throws GitAPIException, IOException {
        VersionProvider provider;

        if(configuration.getFolderConfiguration() != null){
            provider = createFolderProvider(configuration.getFolderConfiguration());
        }
        else if(configuration.getGitConfiguration() != null){
            provider = createGitProvider(configuration.getGitConfiguration());
        }
        else{
            throw new InvalidConfigurationException("Configuration should have a folder or git section");
        }

        return provider;
    }

    private static EvolutionExport createExporter(OutputConfiguration configuration){
        Map<EvolutionExport.Statistics, File> outputFiles = new HashMap<>();

        File smellsCsvFile = configuration.getSmellsCsvFile();
        if(smellsCsvFile != null){
            outputFiles.put(EvolutionExport.Statistics.SMELL, smellsCsvFile);
        }

        return new EvolutionExport(outputFiles);
    }

    private static VersionProvider createFolderProvider(FolderConfiguration configuration){
        throw new NotImplementedException("fromFolder method not implemented yet");
    }

    private static VersionProvider createGitProvider(GitConfiguration configuration) throws IOException, GitAPIException {
        final GitProvider provider = new GitProvider(configuration.getFrequency());

        for(GitLocation location: configuration.getLocations()){
            final File repositoryFolder = new File(provider.getRootFolder(), FilenameUtils.getBaseName(location.getUrl()));

            final LocalRepository localRepository = GitUtils.loadCurrentRepository(
                    location.getUrl(),
                    configuration.getToken(),
                    repositoryFolder,
                    configuration.getBranch()
            );

            List<GitCommit> commits = GitUtils.getCommits(localRepository.getGit(),
                    configuration.getStartDate(),
                    configuration.getEndDate(),
                    configuration.getBranch()
            );

            commits = Utils.removeIgnoredCommit(commits, configuration.getIgnoreCommits());
            commits = Utils.filterCommitsByFrequency(commits, configuration.getFrequency());
            commits = Utils.truncateCommits(commits, configuration.getMaximumCommitsNumber());
            commits = Utils.removeCommitsWithNoFileChanged(commits, location.getProjectFolders());

            provider.addRepository(localRepository, commits, location.getProjectFolders());
        }

        return provider;
    }
}
