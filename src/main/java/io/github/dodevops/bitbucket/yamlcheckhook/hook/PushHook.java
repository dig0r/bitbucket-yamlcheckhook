package io.github.dodevops.bitbucket.yamlcheckhook.hook;

import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PagedIterable;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import io.github.dodevops.bitbucket.yamlcheckhook.exceptions.InvalidYamlFileException;
import io.github.dodevops.bitbucket.yamlcheckhook.service.ValidationServiceInterface;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * A PreReceiveHook checking all changed yaml files in a push, if they are still valid.
 */
@Named
public class PushHook implements PreRepositoryHook {

    @ComponentImport
    private final CommitService commitService;

    private final ValidationServiceInterface validationService;

    private final Logger log = Logger.getLogger(PushHook.class);

    /**
     * Instantiate the yaml check hook
     *
     * @param validationService the ValidationService
     * @param commitService     Commit service injection
     */

    @Inject
    public PushHook(
        final ValidationServiceInterface validationService,
        final CommitService commitService
    )
    {
        this.validationService = validationService;
        this.commitService = commitService;
    }

    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull final PreRepositoryHookContext preRepositoryHookContext,
                                          @Nonnull final RepositoryHookRequest repositoryHookRequest)
    {

        this.log.debug("Fetching all changes from the push");

        for (final RefChange refChange : repositoryHookRequest.getRefChanges()) {

            this.log.debug("Fetching changes from a refchange");

            final ChangesRequest changesRequest =
                new ChangesRequest.Builder(
                    repositoryHookRequest.getRepository(),
                    refChange.getToHash()
                )
                    .sinceId(refChange.getFromHash())
                    .build();

            final Iterable<Change> changes = new PagedIterable<>(
                pageRequest -> this.commitService.getChanges(
                    changesRequest,
                    pageRequest
                ),
                PageRequest.MAX_PAGE_LIMIT
            );

            final Repository repository = repositoryHookRequest.getRepository();
            final String currentHash = refChange.getToHash();

            try {
                this.validationService.areChangesValid(changes, repository, currentHash);
            } catch (final InvalidYamlFileException e) {
                log.debug("Rejecting invalid file", e);
                return RepositoryHookResult.rejected(
                    "Invalid YAML content detected",
                    String.format(
                        "Invalid YAML content detected when " +
                            "reading file %s: %s",
                        e.getFilePath(),
                        e.getScannerMessage()
                    )
                );

            }

        }

        return RepositoryHookResult.accepted();
    }
}
