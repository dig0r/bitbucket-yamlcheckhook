package io.github.dodevops.bitbucket.yamlcheckhook.service;

import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeType;
import com.atlassian.bitbucket.content.ContentService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import io.github.dodevops.bitbucket.yamlcheckhook.exceptions.InvalidYamlFileException;
import io.github.dodevops.bitbucket.yamlcheckhook.hook.PullRequestHook;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.scanner.ScannerException;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.regex.Pattern;

@Service
public class DefaultValidationService implements ValidationServiceInterface {

    @ComponentImport
    private final ContentService contentService;

    private final Logger log = Logger.getLogger(PullRequestHook.class);

    @Inject
    public DefaultValidationService(final ContentService contentService) {
        this.contentService = contentService;
    }

    public void areChangesValid(
        final Iterable<Change> changes,
        final Repository repository,
        final String currentHash
    ) throws InvalidYamlFileException
    {

        final Pattern validExtensions = Pattern.compile("ya?ml", Pattern.CASE_INSENSITIVE);

        for (final Change change : changes) {

            if (change.getType() != ChangeType.ADD && change.getType() != ChangeType.MODIFY) {
                this.log.debug("Ignoring changes other then ADD or MODIFY");
                continue;
            }

            if (StringUtils.isEmpty(change.getPath().getExtension())) {
                this.log.debug("Ignoring changes for files without extensions.");
                continue;
            }

            if (!validExtensions.matcher(change.getPath().getExtension()).matches()) {
                this.log.debug("Ignoring changes from files other than yaml and yml.");
                continue;
            }

            this.log.debug(
                String.format(
                    "Found a yaml file at %s. Checking.",
                    change.getPath().toString()
                )
            );

            final ByteArrayOutputStream fileContentStream =
                new ByteArrayOutputStream();

            this.contentService.streamFile(
                repository,
                currentHash,
                change.getPath().toString(),
                s -> fileContentStream
            );

            final String fileContent = fileContentStream.toString();

            if (StringUtils.isNotEmpty(fileContent)) {

                final Yaml yaml = new Yaml();

                try {

                    yaml.load(fileContent);

                } catch (final ScannerException e) {

                    this.log.error(
                        String.format(
                            "File %s is not a valid yaml file. " +
                                "Rejecting.",
                            change.getPath().toString()
                        ),
                        e
                    );

                    throw (
                        new InvalidYamlFileException(
                            change.getPath().toString(),
                            e.getMessage()
                        )
                    );

                }

            }

        }
    }

}
