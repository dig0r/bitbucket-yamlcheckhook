package it.io.github.dodevops.bitbucket.yamlcheckhook;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;

public class PushTest {

    @Test()
    public void TestPushInvalidFile() throws IOException, GitAPIException {
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

            // Create an invalid Yaml file

            Files.write(
                Paths.get(localPath.getAbsolutePath(), "invalidPush.yaml"),
                Arrays.asList(
                    "b1:",
                    "  b2",
                    "    b3: true"
                ),
                Charset.forName("UTF-8")
            );

            git.add().addFilepattern("invalidPush.yaml").call();

            git.commit().setMessage("Invalid file").call();

            final Iterable<PushResult> results = git.push().setCredentialsProvider(credentialsProvider).call();

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

        } finally {
            FileUtils.deleteDirectory(localPath);
        }

    }

    @Test()
    public void TestPushRemoveFile() throws IOException, GitAPIException {
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

            // Create an invalid Yaml file

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

            Assert.assertEquals(
                "Can not push valid file",
                results.iterator().next().getRemoteUpdates().iterator().next().getStatus(),
                RemoteRefUpdate.Status.OK
            );

            Files.delete(Paths.get(localPath.getAbsolutePath(), "existing.yaml"));
            git.add().addFilepattern("existing.yaml").call();

            git.commit().setMessage("Removed file").call();

            final Iterable<PushResult> removePushResults = git.push().setCredentialsProvider(credentialsProvider).call();

            Assert.assertEquals(
                "Can not push valid file",
                removePushResults.iterator().next().getRemoteUpdates().iterator().next().getStatus(),
                RemoteRefUpdate.Status.OK
            );

        } finally {
            FileUtils.deleteDirectory(localPath);
        }

    }
}
