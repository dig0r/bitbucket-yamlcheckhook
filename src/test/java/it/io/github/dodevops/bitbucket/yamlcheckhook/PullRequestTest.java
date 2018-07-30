package it.io.github.dodevops.bitbucket.yamlcheckhook;

import com.atlassian.bitbucket.auth.AuthenticationService;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.project.ProjectCreateRequest;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestDeleteRequest;
import com.atlassian.bitbucket.pull.PullRequestMergeRequest;
import com.atlassian.bitbucket.pull.PullRequestMergeVetoedException;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryForkRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;
import org.apache.commons.collections.SetUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

@Scanned
@RunWith(AtlassianPluginsTestRunner.class)
public class PullRequestTest {

    private final RepositoryService _repositoryService;

    private final ProjectService _projectService;

    private final PullRequestService _pullRequestService;

    private final AuthenticationService _authenticationService;

    private Repository _sourceRepository;

    private Project _destinationProject;
    private Repository _destinationRepository;

    @Inject
    public PullRequestTest(final RepositoryService repositoryService,
                           final ProjectService projectService,
                           final PullRequestService pullRequestService,
                           final AuthenticationService authenticationService)
    {
        this._repositoryService = repositoryService;
        this._projectService = projectService;
        this._pullRequestService = pullRequestService;
        this._authenticationService = authenticationService;
    }

    @Before
    public void setup() {
        this._authenticationService.set(this._authenticationService.authenticate("admin", "admin"));
        this._sourceRepository = this._repositoryService
            .findByProjectKey("PROJECT_1", new PageRequestImpl(0, 1))
            .getValues()
            .iterator()
            .next();

        this._destinationProject =
            this._projectService.create(new ProjectCreateRequest.Builder().key("FORKDEST")
                .name("Forking destination")
                .build());

        this._destinationRepository = this._repositoryService.fork(
            new RepositoryForkRequest.Builder(this._sourceRepository).project(this._destinationProject).build()
        );
    }

    @After
    public void destroy() {
        this._repositoryService.delete(this._destinationRepository);
        this._projectService.delete(this._destinationProject);
    }

    @Test()
    public void TestPullInvalidFile() throws IOException, GitAPIException {

        final File localPath = File.createTempFile("YamlCheckHookTestGitRepository", "");
        Assert.assertTrue("Can not create temporary path", localPath.delete());

        try {

            final UsernamePasswordCredentialsProvider credentialsProvider =
                new UsernamePasswordCredentialsProvider("admin", "admin");
            final Git git = Git.cloneRepository()
                .setURI("http://admin@localhost:7990/bitbucket/scm/forkdest/rep_1.git")
                .setCredentialsProvider(credentialsProvider)
                .setDirectory(localPath)
                .call();

            // Create an invalid Yaml file

            Files.write(
                Paths.get(localPath.getAbsolutePath(), "invalid.yaml"),
                Arrays.asList(
                    "b1:",
                    "  b2",
                    "    b3: true"
                ),
                Charset.forName("UTF-8")
            );

            git.add().addFilepattern("invalid.yaml").call();

            git.commit().setMessage("Invalid file").call();

            final Iterable<PushResult> results = git.push().setCredentialsProvider(credentialsProvider).call();

            final PullRequest test_pull_request = this._pullRequestService.create(
                "Test pull request",
                "Test pull request with an invalid file",
                (Set<String>) SetUtils.EMPTY_SET,
                this._destinationRepository,
                "refs/heads/master",
                this._sourceRepository,
                "refs/heads/master"
            );

            try {

                this._pullRequestService.merge(new PullRequestMergeRequest.Builder(test_pull_request).build());
                Assert.fail("Pull Request was merged inexpectedly");
            } catch (final PullRequestMergeVetoedException e) {
                // okay.
            }

        } finally {
            FileUtils.deleteDirectory(localPath);
        }

    }

    @Test()
    public void TestPullRemoveFile() throws IOException, GitAPIException {
        final File localPath = File.createTempFile("YamlCheckHookTestGitRepository", "");
        Assert.assertTrue("Can not create temporary path", localPath.delete());

        try {

            final UsernamePasswordCredentialsProvider credentialsProvider =
                new UsernamePasswordCredentialsProvider("admin", "admin");
            final Git git = Git.cloneRepository()
                .setURI("http://admin@localhost:7990/bitbucket/scm/project_1/rep_1.git")
                .setCredentialsProvider(credentialsProvider)
                .setDirectory(localPath)
                .call();

            // Create a valid Yaml file

            Files.write(
                Paths.get(localPath.getAbsolutePath(), "existing.yaml"),
                Arrays.asList(
                    "b1:",
                    "  b2:",
                    "    b3: true"
                ),
                Charset.forName("UTF-8")
            );

            git.add().addFilepattern("existing.yaml").call();

            git.commit().setMessage("Existing file").call();

            final Iterable<PushResult> results = git.push().setCredentialsProvider(credentialsProvider).call();

            final PullRequest test_pull_request = this._pullRequestService.create(
                "Test pull request",
                "Test pull request with a removed file",
                (Set<String>) SetUtils.EMPTY_SET,
                this._sourceRepository,
                "refs/heads/master",
                this._destinationRepository,
                "refs/heads/master"
            );

            this._pullRequestService.merge(new PullRequestMergeRequest.Builder(test_pull_request).build());

        } finally {
            FileUtils.deleteDirectory(localPath);
        }

    }
}
