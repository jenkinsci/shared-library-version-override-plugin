package io.jenkins.plugins.shared_library_version_override;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryResolver;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.verb.POST;

/**
 * Shared library version override configuration
 *
 * @author Cyril Pottiers
 */
public class LibraryCustomConfiguration extends AbstractDescribableImpl<LibraryCustomConfiguration> {
    private static final Logger LOGGER = Logger.getLogger(LibraryCustomConfiguration.class.getName());

    public String name;
    public String version;

    @DataBoundConstructor
    public LibraryCustomConfiguration(String name, String version) {
        this.name = Util.fixEmptyAndTrim(name);
        this.version = Util.fixEmptyAndTrim(version);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<LibraryCustomConfiguration> {

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
            if (version.isEmpty()) {
                return FormValidation.ok();
            } else {
                for (LibraryResolver resolver : ExtensionList.lookup(LibraryResolver.class)) {
                    for (LibraryConfiguration config : resolver.fromConfiguration(Stapler.getCurrentRequest())) {
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
            Collection<LibraryConfiguration> libs = FolderConfigurations.getDefinedLibrariesForGroup(group);
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
            Collection<LibraryConfiguration> libs = FolderConfigurations.getDefinedLibrariesForGroup(group);
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
