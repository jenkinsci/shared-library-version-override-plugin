package io.jenkins.plugins.shared_library_version_override;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@WithGitSampleRepo
class LibraryCustomConfigurationTest {

    private JenkinsRule r;

    private GitSampleRepoRule sampleRepo;

    @BeforeEach
    void initNewRepository(JenkinsRule rule, GitSampleRepoRule repo) throws Exception {
        r = rule;
        sampleRepo = repo;

        // Create sample repo
        sampleRepo.init();
        sampleRepo.write("vars/greet.groovy", "def call(recipient) {echo(/hello from $recipient/)}");
        sampleRepo.write("src/pkg/Clazz.groovy", "package pkg; class Clazz {static String whereAmI() {'master'}}");
        sampleRepo.git("add", "vars", "src");
        sampleRepo.git("commit", "--message=init");

        sampleRepo.git("checkout", "-b", "develop");
        sampleRepo.write("src/pkg/Clazz.groovy", "package pkg; class Clazz {static String whereAmI() {'develop'}}");
        sampleRepo.git("commit", "--all", "--message=branching");

        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
    }

    @Test
    void validNameAndVersion() {
        String libraryName = "  greet   ";
        String defaultVersion = "   master   ";

        LibraryCustomConfiguration item = new LibraryCustomConfiguration();
        item.setName(libraryName);
        item.setVersion(defaultVersion);

        assertEquals("greet", item.getName());
        assertEquals("master", item.getVersion());
        assertEquals("*", item.getNameFilter());
    }

    @Test
    public void defaultsBranchFilterToWildcard() {
        LibraryCustomConfiguration item = new LibraryCustomConfiguration();
        item.setName("greet");
        item.setVersion("master");
        item.setNameFilter(" ");
        assertEquals("*", item.getNameFilter());
    }
}
