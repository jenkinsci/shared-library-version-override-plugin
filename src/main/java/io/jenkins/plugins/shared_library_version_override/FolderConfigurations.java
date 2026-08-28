package io.jenkins.plugins.shared_library_version_override;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.libs.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A class that holds a list of overrides configurations for a folder
 *
 * @author Cyril Pottiers
 */
public class FolderConfigurations extends AbstractFolderProperty<AbstractFolder<?>> {
    private static final Logger LOGGER = Logger.getLogger(FolderConfigurations.class.getName());

    private List<LibraryCustomConfiguration> overrides = Collections.emptyList();

    @DataBoundConstructor
    public FolderConfigurations() {}

    /**
     * Returns the overrides configurations added to the folder
     *
     * @return The list of overrides configurations added to the folder
     */
    public LibraryCustomConfiguration[] getOverrides() {
        LibraryCustomConfiguration[] lcc = overrides.toArray(new LibraryCustomConfiguration[0]);
        LOGGER.log(Level.FINER, "get overrides : ({0})\n", lcc);
        return lcc;
    }

    /**
     * Adds a bunch of overrides configurations to the folder
     *
     * @param items The list of overrides configurations to be added to the folder
     */
    @DataBoundSetter
    public void setOverrides(List<LibraryCustomConfiguration> items) {
        LOGGER.log(Level.FINER, "Add new overrides : ({0})\n", items);
        this.overrides = items;
    }

    /**
     * Descriptor class.
     */
    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return FolderConfigurations.class.getName();
        }
    }

    /**
     * Return all known LibraryConfigurations for an ItemGroup
     * @param group the context
     * @return the known LibraryConfigurations
     */
    public static Collection<LibraryConfiguration> getAllLibrariesForGroup(ItemGroup<?> group) {
        List<LibraryConfiguration> libraries = new ArrayList<>();
        GlobalLibraries libs = GlobalLibraries.get();
        libraries.addAll(libs.getLibraries());
        libraries.addAll(getAllUntrustedLibrariesForGroup(group));
        LOGGER.log(
                Level.FINE,
                "FolderConfigurations.getAllLibrariesForGroup {0}\n",
                libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
        return libraries;
    }

    /**
     * Return all known Untrusted LibraryConfigurations for an ItemGroup
     * @param group the context
     * @return the known Untrusted LibraryConfigurations
     */
    public static Collection<LibraryConfiguration> getAllUntrustedLibrariesForGroup(ItemGroup<?> group) {
        List<LibraryConfiguration> libraries = new ArrayList<>();
        // Get all global untrusted libraries
        GlobalUntrustedLibraries libs = GlobalUntrustedLibraries.get();
        libraries.addAll(libs.getLibraries());

        // Get all folder local libraries
        for (ItemGroup<?> g = group; g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
            AbstractFolder<?> f = (AbstractFolder<?>) g;
            FolderLibraries prop = f.getProperties().get(FolderLibraries.class);
            if (prop != null) {
                libraries.addAll(prop.getLibraries());
            }
        }
        LOGGER.log(
                Level.FINE,
                "FolderConfigurations.getAllUntrustedLibrariesForGroup {0}\n",
                libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
        return libraries;
    }

    /**
     * Return a copy of a LibraryConfiguration with a new version, if allowed
     * @param item the override configuration desired
     * @param libs the LibraryConfigurations known for the current context
     * @return the copy of the LibraryConfiguration with the new version or null if library don't allow version override
     */
    public static LibraryConfiguration getLibraryConfiguration(
            LibraryCustomConfiguration item, Collection<LibraryConfiguration> libs) {
        LibraryConfiguration libConfig = null;
        for (LibraryConfiguration lib : libs) {
            if (lib.getName().equals(item.getName())) {
                // if original library don't allow version override, so don't take it
                if (!lib.isAllowVersionOverride()) {
                    LOGGER.log(
                            Level.FINE,
                            "FolderConfigurations.getLibraryConfiguration {0} don't allow version override, don't take it.\n",
                            lib.getName());
                    continue;
                }
                libConfig = new LibraryConfiguration(lib.getName(), lib.getRetriever());
                libConfig.setDefaultVersion(item.getVersion());
                libConfig.setImplicit(lib.isImplicit());
                libConfig.setAllowVersionOverride(lib.isAllowVersionOverride());
                libConfig.setIncludeInChangesets(lib.getIncludeInChangesets());
                libConfig.setCachingConfiguration(lib.getCachingConfiguration());
            }
        }
        return libConfig;
    }

    /**
     * Return the resolved library configurations for a given job.
     *
     * @param job the Jenkins job
     * @param allLibs all available library configurations
     * @return the list of resolved library configurations
     */
    public static List<LibraryConfiguration> getResolvedLibrariesForJob(
            Job<?, ?> job, Collection<LibraryConfiguration> allLibs) {
        List<LibraryConfiguration> libraries = new ArrayList<>();
        for (ItemGroup<?> g = job.getParent(); g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
            AbstractFolder<?> f = (AbstractFolder<?>) g;
            if (f.hasPermission(Item.CONFIGURE)) {
                FolderConfigurations prop = f.getProperties().get(FolderConfigurations.class);
                if (prop != null) {
                    for (LibraryCustomConfiguration item : prop.getOverrides()) {
                        // check item filters for the current job
                        if (!item.isApplicableToJob(job)) {
                            continue;
                        }

                        LibraryConfiguration libConfig = getLibraryConfiguration(item, allLibs);
                        if (libConfig != null) {
                            libraries.add(libConfig);
                        }
                    }
                }
            }
        }
        return libraries;
    }

    /**
     * Simulate a new LibraryResolver for Trusted Libraries (Global-level Libraries)
     */
    @Extension(ordinal = 1000) // Priority over pipeline-groovy-lib
    public static class CustomTrustedLibraryResolver extends LibraryResolver {

        @Override
        public boolean isTrusted() {
            return true;
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> forJob(
                @NonNull Job<?, ?> job, @NonNull Map<String, String> libraryVersions) {
            // Get all global libraries
            List<LibraryConfiguration> libraries =
                    getResolvedLibrariesForJob(job, GlobalLibraries.get().getLibraries());
            LOGGER.log(
                    Level.FINE,
                    "CustomFolderLibraryResolver.forJob {0}\n",
                    libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
            return libraries;
        }
    }

    /**
     * Simulate a new LibraryResolver for Untrusted libraries (Folder-level Libraries)
     */
    @Extension(ordinal = 1000) // Priority over pipeline-groovy-lib
    public static class CustomUntrustedLibraryResolver extends LibraryResolver {

        @Override
        public boolean isTrusted() {
            return false;
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> forJob(
                @NonNull Job<?, ?> job, @NonNull Map<String, String> libraryVersions) {
            // Get all untrusted libraries
            List<LibraryConfiguration> libraries =
                    getResolvedLibrariesForJob(job, getAllUntrustedLibrariesForGroup(job.getParent()));
            LOGGER.log(
                    Level.FINE,
                    "CustomUntrustedLibraryResolver.forJob {0}\n",
                    libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
            return libraries;
        }
    }
}
