package io.github.dodevops.bitbucket.yamlcheckhook.service;

import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.repository.Repository;
import io.github.dodevops.bitbucket.yamlcheckhook.exceptions.InvalidYamlFileException;

/**
 * A service to validate added or modified yaml files in a number of changes
 */
public interface ValidationServiceInterface {
    /**
     * Check all changes for added or modified yaml files and validate them
     *
     * @param changes The changes to check
     * @param repository The repository to check in
     * @param currentHash The current hash of the repository to fetch the yaml files
     * @throws InvalidYamlFileException One YAML file had an invalid content
     */
    void areChangesValid(
        final Iterable<Change> changes,
        final Repository repository,
        final String currentHash
    ) throws InvalidYamlFileException;
}
