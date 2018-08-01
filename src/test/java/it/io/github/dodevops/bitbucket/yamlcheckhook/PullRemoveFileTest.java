package it.io.github.dodevops.bitbucket.yamlcheckhook;

import com.atlassian.bitbucket.auth.AuthenticationService;
import com.atlassian.bitbucket.project.Project;
import com.atlassian.bitbucket.project.ProjectCreateRequest;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestMergeRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryForkRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

@Scanned
@RunWith(AtlassianPluginsTestRunner.class)
public class PullRemoveFileTest {

    @ComponentImport
    private final RepositoryService _repositoryService;

    @ComponentImport
    private final ProjectService _projectService;

    @ComponentImport
    private final PullRequestService _pullRequestService;

    @ComponentImport
    private final AuthenticationService _authenticationService;

    private Repository _sourceRepository;

    private Project _destinationProject;

    private Repository _destinationRepository;

    private File _tmpGitRepositoryPath;

    private CredentialsProvider _credentialsProvider;

    private Git _git;

    @Inject
    public PullRemoveFileTest(final RepositoryService repositoryService,
        final ProjectService projectService,
        final PullRequestService pullRequestService,
        final AuthenticationService authenticationService) {

        this._repositoryService = repositoryService;
        this._projectService = projectService;
        this._pullRequestService = pullRequestService;
        this._authenticationService = authenticationService;
    }

    @Before
    public void setup() throws IOException, GitAPIException {
        this._authenticationService.set(this._authenticationService.authenticate("admin", "admin"));
        this._sourceRepository = this._repositoryService
            .findByProjectKey("PROJECT_1", new PageRequestImpl(0, 1))
            .getValues()
            .iterator()
            .next();

        this._destinationProject =
            this._projectService.create(
                new ProjectCreateRequest.Builder()
                    .key("FORKDEST")
                    .name("Forking destination")
                    .build()
            );

        this._destinationRepository = this._repositoryService.fork(
            new RepositoryForkRequest.Builder(this._sourceRepository)
                .project(this._destinationProject)
                .build()
        );

        this._tmpGitRepositoryPath = File.createTempFile("YamlCheckHookTestGitRepository", "");
        Assert.assertTrue("Can not create temporary path", _tmpGitRepositoryPath.delete());

        this._credentialsProvider = new UsernamePasswordCredentialsProvider("admin", "admin");

        this._git = Git.cloneRepository()
            .setURI("http://admin@localhost:7990/bitbucket/scm/project_1/rep_1.git")
            .setCredentialsProvider(this._credentialsProvider)
            .setDirectory(this._tmpGitRepositoryPath)
            .call();
    }

    @After
    public void destroy() throws IOException {
        this._repositoryService.delete(this._destinationRepository);
        this._projectService.delete(this._destinationProject);

        this._git.close();
        FileUtils.deleteDirectory(this._tmpGitRepositoryPath);
    }

    @Test()
    public void testPullRemoveFile() throws IOException, GitAPIException {
        // Create a valid Yaml file
        Files.write(
            Paths.get(this._tmpGitRepositoryPath.getAbsolutePath(), "existing.yaml"),
            Arrays.asList(
                "b1:",
                "  b2:",
                "    b3: true"
            ),
            StandardCharsets.UTF_8
        );

        this._git.add().addFilepattern("existing.yaml").call();

        this._git.commit().setMessage("Existing file").call();

        this._git.push().setCredentialsProvider(this._credentialsProvider).call();

        final PullRequest testPullRequest = this._pullRequestService.create(
            "Test pull request",
            "Test pull request with a removed file",
            Collections.emptySet(),
            this._sourceRepository,
            "refs/heads/master",
            this._destinationRepository,
            "refs/heads/master"
        );

        this._pullRequestService.merge(new PullRequestMergeRequest.Builder(testPullRequest).build());
    }
}
