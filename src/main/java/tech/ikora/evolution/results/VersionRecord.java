package tech.ikora.evolution.results;

import tech.ikora.model.Projects;
import tech.ikora.model.TestCase;
import tech.ikora.model.UserKeyword;
import tech.ikora.model.VariableAssignment;

import java.time.Instant;
import java.util.Date;

public class VersionRecord implements Record {
    private final Date date;
    private final int projects;
    private final int testCases;
    private final int userKeywords;
    private final int variables;
    private final int lines;

    public VersionRecord(Projects version){
        this.date = version.getDate() == null ? Date.from(Instant.now()) : version.getDate();
        this.projects = version.size();
        this.testCases = version.getTestCases().size();
        this.userKeywords = version.getUserKeywords().size();
        this.variables = version.getVariableAssignments().size();
        this.lines = version.getLoc();
    }

    public Date getDate() {
        return date;
    }

    public int getProjects() {
        return projects;
    }

    public int getTestCases() {
        return testCases;
    }

    public int getUserKeywords() {
        return userKeywords;
    }

    public int getVariables() {
        return variables;
    }

    public int getLines() {
        return lines;
    }

    @Override
    public String[] getKeys() {
        return new String[]{
                "date",
                "number_projects",
                "number_test_cases",
                "number_keywords",
                "number_variables",
                "number_lines"
        };
    }

    @Override
    public Object[] getValues() {
        return new Object[]{
                this.getDate().toString(),
                this.getProjects(),
                this.getTestCases(),
                this.getUserKeywords(),
                this.getVariables(),
                this.getLines()
        };
    }
}
