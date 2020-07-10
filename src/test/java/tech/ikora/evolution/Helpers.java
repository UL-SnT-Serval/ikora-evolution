package tech.ikora.evolution;

import tech.ikora.evolution.configuration.EvolutionConfiguration;
import tech.ikora.evolution.configuration.FolderConfiguration;
import tech.ikora.evolution.configuration.OutputConfiguration;

import java.io.File;

import static edu.stanford.nlp.util.logging.Redwood.Util.fail;

public class Helpers {
    static EvolutionConfiguration createConfiguration(String resourcesPath){
        File projectFolder = null;
        try {
            projectFolder = tech.ikora.utils.FileUtils.getResourceFile(resourcesPath);
        } catch (Exception e) {
            fail(String.format("Failed to load '%s': %s", resourcesPath, e.getMessage()));
        }

        final FolderConfiguration folderConfiguration = new FolderConfiguration();
        folderConfiguration.setRootFolder(projectFolder);
        folderConfiguration.setNameFormat(FolderConfiguration.NameFormat.VERSION);

        final File outputFolder = new File(new File(System.getProperty("java.io.tmpdir")), resourcesPath);
        outputFolder.deleteOnExit();


        if(!outputFolder.mkdirs()){
            fail(String.format("Failed to create output folder: %s", outputFolder.getAbsolutePath()));
        }

        final OutputConfiguration outputConfiguration = new OutputConfiguration();
        outputConfiguration.setProjectsCsvFile(new File(outputFolder, "projects.csv"));
        outputConfiguration.setSmellsCsvFile(new File(outputFolder, "smells.csv"));

        final EvolutionConfiguration evolutionConfiguration = new EvolutionConfiguration();
        evolutionConfiguration.setFolderConfiguration(folderConfiguration);
        evolutionConfiguration.setOutputConfiguration(outputConfiguration);

        return evolutionConfiguration;
    }
}