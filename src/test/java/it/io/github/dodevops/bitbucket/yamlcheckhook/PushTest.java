package it.io.github.dodevops.bitbucket.yamlcheckhook;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;

public class PushTest {

    private File _tmpGitRepositoryPath;

    private CredentialsProvider _credentialsProvider;

    private Git _git;

    @Before
    public void setup() throws IOException, GitAPIException {
        this._tmpGitRepositoryPath = File.createTempFile("YamlCheckHookTestGitRepository", "");

        Assert.assertTrue("Can not create temporary path", this._tmpGitRepositoryPath.delete());

        this._credentialsProvider = new UsernamePasswordCredentialsProvider("admin", "admin");

        this._git = Git.cloneRepository()
            .setURI("http://admin@localhost:7990/bitbucket/scm/project_1/rep_1.git")
            .setCredentialsProvider(this._credentialsProvider)
            .setDirectory(this._tmpGitRepositoryPath)
            .call();
    }

    @After
    public void destroy() throws IOException {
        this._git.close();
        FileUtils.deleteDirectory(this._tmpGitRepositoryPath);
    }

    @Test()
    public void testPushInvalidFile() throws IOException, GitAPIException {
        // Create an invalid Yaml file
        Files.write(
            Paths.get(this._tmpGitRepositoryPath.getAbsolutePath(), "invalidPush.yaml"),
            Arrays.asList(
                "b1:",
                "  b2",
                "    b3: true"
            ),
            StandardCharsets.UTF_8
        );

        this._git.add().addFilepattern("invalidPush.yaml").call();

        this._git.commit().setMessage("Invalid file").call();

        final Iterable<PushResult> results = this._git.push().setCredentialsProvider(this._credentialsProvider).call();

        final PushResult firstResult = results.iterator().next();

        Assert.assertEquals(
            "Push wasn't rejected",
            firstResult.getRemoteUpdates().iterator().next().getStatus(),
            RemoteRefUpdate.Status.REJECTED_OTHER_REASON
        );

        Assert.assertTrue(
            "Invalid rejection message",
            Pattern.compile("Invalid YAML content detected.*mapping values are not allowed here")
                .matcher(firstResult.getMessages())
                .find()
        );

    }

    @Test()
    public void testPushRemoveFile() throws IOException, GitAPIException {
        // Create an invalid Yaml file
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

        final Iterable<PushResult> results = this._git.push().setCredentialsProvider(this._credentialsProvider).call();

        Assert.assertEquals(
            "Can not push valid file",
            results.iterator().next().getRemoteUpdates().iterator().next().getStatus(),
            RemoteRefUpdate.Status.OK
        );

        Files.delete(Paths.get(this._tmpGitRepositoryPath.getAbsolutePath(), "existing.yaml"));
        this._git.add().addFilepattern("existing.yaml").call();

        this._git.commit().setMessage("Removed file").call();

        final Iterable<PushResult> removePushResults =
            this._git.push().setCredentialsProvider(this._credentialsProvider).call();

        Assert.assertEquals(
            "Can not push valid file",
            removePushResults.iterator().next().getRemoteUpdates().iterator().next().getStatus(),
            RemoteRefUpdate.Status.OK
        );

    }
}
