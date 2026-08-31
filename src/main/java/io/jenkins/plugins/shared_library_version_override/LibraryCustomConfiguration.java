package io.jenkins.plugins.shared_library_version_override;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryResolver;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.verb.POST;

/**
 * Shared library version override configuration
 *
 * @author Cyril Pottiers
 */
public class LibraryCustomConfiguration extends AbstractDescribableImpl<LibraryCustomConfiguration> {
    private static final Logger LOGGER = Logger.getLogger(LibraryCustomConfiguration.class.getName());

    @CheckForNull
    private String name;

    @CheckForNull
    private String version;

    @CheckForNull
    private String nameFilter;

    @DataBoundConstructor
    public LibraryCustomConfiguration() {}

    @CheckForNull
    public String getName() {
        return name;
    }

    @CheckForNull
    public String getVersion() {
        return version;
    }

    @NonNull
    public String getNameFilter() {
        return nameFilter != null ? nameFilter : DescriptorImpl.defaultNameFilter;
    }

    @DataBoundSetter
    public void setName(@NonNull String name) {
        this.name = Util.fixEmptyAndTrim(name);
    }

    @DataBoundSetter
    public void setVersion(@NonNull String version) {
        this.version = Util.fixEmptyAndTrim(version);
    }

    @DataBoundSetter
    public void setNameFilter(@NonNull String nameFilter) {
        this.nameFilter = Util.fixEmptyAndTrim(nameFilter);
    }

    /**
     * Returns the pattern corresponding to the filter containing wildcards.
     *
     * @param filter the filter containing wildcards
     * @return pattern corresponding to the filter containing wildcards
     */
    private String getPattern(String filter) {
        StringBuilder quotedBranches = new StringBuilder();
        for (String wildcard : filter.split(" ")) {
            StringBuilder quotedBranch = new StringBuilder();
            for (String f : wildcard.split("(?=[*])|(?<=[*])")) {
                if (f.equals("*")) {
                    quotedBranch.append(".*");
                } else if (!f.isEmpty()) {
                    quotedBranch.append(Pattern.quote(f));
                }
            }
            if (quotedBranches.length() > 0) {
                quotedBranches.append("|");
            }
            quotedBranches.append(quotedBranch);
        }
        return quotedBranches.toString();
    }

    public boolean isApplicableToJob(Job<?, ?> job) {
        if (job == null) {
            return false;
        }
        return Pattern.matches(getPattern(getNameFilter()), URLDecoder.decode(job.getName(), StandardCharsets.UTF_8));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<LibraryCustomConfiguration> {
        public static final String defaultNameFilter = "*";

        private ItemGroup<?> getItemGroupFromItem(Item item) {
            ItemGroup<?> group = null;
            if (item != null) {
                if (ItemGroup.class.isAssignableFrom(item.getClass())) {
                    group = (ItemGroup<?>) item;
                } else {
                    group = item.getParent();
                }
            }
            return group;
        }

        @POST
        public FormValidation doCheckVersion(
                @AncestorInPath Item item, @QueryParameter String version, @QueryParameter String name) {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                LOGGER.log(Level.FINE, "DescriptorImpl.doCheckVersion for item null\n");
            } else {
                item.checkPermission(Item.CONFIGURE);
                LOGGER.log(Level.FINE, "DescriptorImpl.doCheckVersion for item {0}\n", item.getName());
            }

            if (version.isEmpty()) {
                return FormValidation.ok();
            } else {
                for (LibraryResolver resolver : ExtensionList.lookup(LibraryResolver.class)) {
                    for (LibraryConfiguration config : resolver.fromConfiguration(Stapler.getCurrentRequest2())) {
                        if (config.getName().equals(name)) {
                            return config.getRetriever().validateVersion(name, version, item);
                        }
                    }
                }
                return FormValidation.ok("Cannot validate default version until after saving and reconfiguring.");
            }
        }

        @POST
        public ListBoxModel doFillNameItems(@AncestorInPath Item item) {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                LOGGER.log(Level.FINE, "DescriptorImpl.doFillNameItems for item null\n");
            } else {
                item.checkPermission(Item.CONFIGURE);
                LOGGER.log(Level.FINE, "DescriptorImpl.doFillNameItems for item {0}\n", item.getName());
            }

            Set<String> libNames = new TreeSet<>();
            ItemGroup<?> group = getItemGroupFromItem(item);
            Collection<LibraryConfiguration> libs = FolderConfigurations.getAllLibrariesForGroup(group);
            for (LibraryConfiguration lib : libs) {
                libNames.add(lib.getName());
            }

            ListBoxModel items = new ListBoxModel();
            for (String libName : libNames) {
                items.add(new ListBoxModel.Option(libName));
            }
            return items;
        }

        @POST
        public FormValidation doValidate(
                @QueryParameter("name") final String name,
                @QueryParameter("version") final String version,
                @AncestorInPath Item item) {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                item.checkPermission(Item.CONFIGURE);
            }

            List<FormValidation> validations = new ArrayList<>();
            // Check name existence and version override allowance
            ItemGroup<?> group = getItemGroupFromItem(item);
            Collection<LibraryConfiguration> libs = FolderConfigurations.getAllLibrariesForGroup(group);
            LibraryConfiguration lib = libs.stream()
                    .filter(l -> l.getName().equals(name))
                    .findFirst()
                    .orElse(null);
            if (lib == null) {
                validations.add(FormValidation.error(Messages.LibraryCustomConfiguration_Validation_NameUnknown()));
            } else if (!lib.isAllowVersionOverride()) {
                validations.add(
                        FormValidation.error(Messages.LibraryCustomConfiguration_Validation_ImmutableVersion()));
            }
            if (version.isEmpty()) {
                validations.add(FormValidation.error(Messages.LibraryCustomConfiguration_Validation_EmptyVersion()));
            }
            // check version existence
            if (lib != null) {
                FormValidation versionValidation = lib.getRetriever().validateVersion(name, version, item);
                if (versionValidation.kind != FormValidation.Kind.OK) {
                    validations.add(
                            FormValidation.error(Messages.LibraryCustomConfiguration_Validation_UnknownVersion()));
                }
            }

            if (validations.isEmpty()) {
                return FormValidation.ok(Messages.LibraryCustomConfiguration_Validation_Success());
            }
            return FormValidation.aggregate(validations);
        }
    }
}
