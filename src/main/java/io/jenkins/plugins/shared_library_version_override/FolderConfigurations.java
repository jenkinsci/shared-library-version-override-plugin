package io.jenkins.plugins.shared_library_version_override;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryResolver;
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
     * Return all known LibraryConfigurations including Global Libraries
     * @param group the context
     * @return the known LibraryConfigurations
     */
    public static Collection<LibraryConfiguration> getDefinedLibrariesForGroup(ItemGroup<?> group) {
        return getDefinedLibrariesForGroup(group, true);
    }

    /**
     * Return all known LibraryConfigurations including or not Global Libraries
     * @param group the context
     * @param includeGlobalLibraries if true then Global Libraries are not returned
     * @return the known LibraryConfigurations
     */
    public static Collection<LibraryConfiguration> getDefinedLibrariesForGroup(
            ItemGroup<?> group, boolean includeGlobalLibraries) {
        List<LibraryConfiguration> libraries = new ArrayList<>();
        // Get all global librairies
        if (includeGlobalLibraries) {
            GlobalLibraries libs = ExtensionList.lookupSingleton(GlobalLibraries.class);
            libraries.addAll(libs.getLibraries());
        }
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
                "FolderConfigurations.getDefinedLibrariesForGroup {0}\n",
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
     * Simulate a new LibraryResolver for Trusted Libraries (Global-level Libraries)
     */
    @Extension(ordinal = 1000) // Priority over pipeline-groovy-lib
    public static class CustomTrustedLibraryResolver extends LibraryResolver {

        @Override
        public boolean isTrusted() {
            return true;
        }

        private Collection<LibraryConfiguration> forGroup(@CheckForNull ItemGroup<?> group, boolean checkPermission) {
            // Get all global libraries
            Collection<LibraryConfiguration> allLibs =
                    ExtensionList.lookupSingleton(GlobalLibraries.class).getLibraries();
            List<LibraryConfiguration> libraries = new ArrayList<>();
            for (ItemGroup<?> g = group; g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
                AbstractFolder<?> f = (AbstractFolder<?>) g;
                if (!checkPermission || f.hasPermission(Item.CONFIGURE)) {
                    FolderConfigurations prop = f.getProperties().get(FolderConfigurations.class);
                    if (prop != null) {
                        for (LibraryCustomConfiguration item : prop.getOverrides()) {
                            LibraryConfiguration libConfig = getLibraryConfiguration(item, allLibs);
                            if (libConfig != null) {
                                libraries.add(libConfig);
                            }
                        }
                    }
                }
            }
            LOGGER.log(
                    Level.FINE,
                    "CustomFolderLibraryResolver.forGroup {0}\n",
                    libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
            return libraries;
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> forJob(
                @NonNull Job<?, ?> job, @NonNull Map<String, String> libraryVersions) {
            return forGroup(job.getParent(), false);
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

        private Collection<LibraryConfiguration> forGroup(@CheckForNull ItemGroup<?> group, boolean checkPermission) {
            // Get all folder-level libraries
            Collection<LibraryConfiguration> allLibs = getDefinedLibrariesForGroup(group, false);
            List<LibraryConfiguration> libraries = new ArrayList<>();
            for (ItemGroup<?> g = group; g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
                AbstractFolder<?> f = (AbstractFolder<?>) g;
                if (!checkPermission || f.hasPermission(Item.CONFIGURE)) {
                    FolderConfigurations prop = f.getProperties().get(FolderConfigurations.class);
                    if (prop != null) {
                        for (LibraryCustomConfiguration item : prop.getOverrides()) {
                            LibraryConfiguration libConfig = getLibraryConfiguration(item, allLibs);
                            if (libConfig != null) {
                                libraries.add(libConfig);
                            }
                        }
                    }
                }
            }
            LOGGER.log(
                    Level.FINE,
                    "CustomUntrustedLibraryResolver.forGroup {0}\n",
                    libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
            return libraries;
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> forJob(
                @NonNull Job<?, ?> job, @NonNull Map<String, String> libraryVersions) {
            return forGroup(job.getParent(), false);
        }
    }
}
