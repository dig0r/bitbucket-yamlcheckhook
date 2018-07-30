package io.github.dodevops.bitbucket.yamlcheckhook.hook;

import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.PullRequestMergeHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeCheck;
import com.atlassian.bitbucket.pull.PullRequestDeclineRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.pull.PullRequestService;
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
 * A PreReceiveHook checking all changed yaml files of a pull request, if they are still valid.
 */
@Named
public class PullRequestHook implements RepositoryMergeCheck {


    @ComponentImport
    private final CommitService commitService;

    @ComponentImport
    private final PullRequestService pullRequestService;

    private final ValidationServiceInterface validationService;

    private final Logger log = Logger.getLogger(PullRequestHook.class);

    /**
     * Instantiate the yaml check hook
     *
     * @param commitService  Commit service injection
     * @param pullRequestService Pull request service for communication
     * @param validationService Validation service
     */

    @Inject
    public PullRequestHook(
        final CommitService commitService,
        final PullRequestService pullRequestService,
        final ValidationServiceInterface validationService
    )
    {
        this.commitService = commitService;
        this.pullRequestService = pullRequestService;
        this.validationService = validationService;
    }

    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull final PreRepositoryHookContext preRepositoryHookContext,
                                          @Nonnull final PullRequestMergeHookRequest pullRequestMergeHookRequest)
    {
        this.log.debug(
            String.format(
                "Fetching all changes from pull request %s by %s",
                pullRequestMergeHookRequest.getPullRequest().getTitle(),
                pullRequestMergeHookRequest.getPullRequest().getAuthor().getUser().getDisplayName()
            )
        );

        final PullRequestRef fromRef = pullRequestMergeHookRequest.getPullRequest()
            .getFromRef();
        final PullRequestRef toRef = pullRequestMergeHookRequest.getPullRequest()
            .getToRef();
        final ChangesRequest changesRequest =
            new ChangesRequest.Builder(
                fromRef.getRepository(),
                fromRef.getLatestCommit()
            )
                .sinceId(toRef.getLatestCommit())
                .build();

        final Iterable<Change> changes = new PagedIterable<>(
            pageRequest -> this.commitService.getChanges(
                changesRequest,
                pageRequest
            ),
            PageRequest.MAX_PAGE_LIMIT
        );

        try {
            this.validationService.areChangesValid(
                changes,
                fromRef.getRepository(),
                fromRef.getLatestCommit()
            );
        } catch (final InvalidYamlFileException e) {
            log.debug("Vetoing because of an invalid yaml file", e);

            final String comment = String.format(
                "Invalid YAML content detected when reading file %s: \n" +
                    "```\n%s```",
                e.getFilePath(),
                e.getScannerMessage()
            );

            this.pullRequestService.decline(
                new PullRequestDeclineRequest.Builder(
                    pullRequestMergeHookRequest.getPullRequest(),
                    pullRequestMergeHookRequest.getPullRequest().getVersion()
                ).comment(
                    comment
                ).build()
            );

            return RepositoryHookResult.rejected(
                "Invalid YAML content detected",
                comment
            );

        }

        return RepositoryHookResult.accepted();
    }

}
