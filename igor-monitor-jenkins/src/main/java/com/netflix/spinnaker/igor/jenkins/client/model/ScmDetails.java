package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;

/**
 * Represents git details
 *
 * <p>The serialization of these details in the Jenkins build XML changed in version 4.0.0 of the
 * jenkins-git plugin.
 *
 * <p>Prior to 4.0.0, the format was: <action _class="hudson.plugins.git.util.BuildData">
 * <lastBuiltRevision> <branch> <SHA1>943a702d06f34599aee1f8da8ef9f7296031d699</SHA1>
 * <name>refs/remotes/origin/master</name> </branch> </lastBuiltRevision>
 * <remoteUrl>some-url</remoteUrl> </action>
 *
 * <p>As of version 4.0.0, the format is: <action _class="hudson.plugins.git.util.BuildDetails">
 * <build> <revision> <branch> <SHA1>943a702d06f34599aee1f8da8ef9f7296031d699</SHA1>
 * <name>refs/remotes/origin/master</name> </branch> </revision> <remoteUrl>some-url</remoteUrl>
 * </build> </action>
 *
 * <p>The code in this module should remain compatible with both formats to ensure that SCM info is
 * populated in Spinnaker regardless of which version of the jenkins-git plugin is being used.
 */
public class ScmDetails {
  /** TODO(rz): Rename to gitRevisions */
  public List<GenericGitRevision> genericGitRevisions() {
    List<GenericGitRevision> genericGitRevisions = new ArrayList<>();

    if (actions == null) {
      return null;
    }

    for (Action action : actions) {
      final Revision lastBuiltRevision = (action == null ? null : action.getLastBuiltRevision());
      final ScmBuild build = (action == null ? null : action.getBuild());
      final Revision revision =
          (lastBuiltRevision == null)
              ? (build == null ? null : build.getRevision())
              : lastBuiltRevision;
      final List<Branch> branch = (revision == null ? null : revision.getBranch());

      if (branch != null && !branch.isEmpty()) {
        genericGitRevisions.addAll(
            branch.stream()
                .map(
                    b ->
                        GenericGitRevision.builder()
                            .name(b.getName())
                            .branch(b.getSimpleBranchName())
                            .sha1(b.getSha1())
                            .remoteUrl(action.getRemoteUrl())
                            .build())
                .collect(Collectors.toList()));
      }
    }

    // If the same revision appears in both the old and new locations in the XML, we only want to
    // return it once.
    return genericGitRevisions.stream().distinct().collect(Collectors.toList());
  }

  public ArrayList<Action> getActions() {
    return actions;
  }

  public void setActions(ArrayList<Action> actions) {
    this.actions = actions;
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "action")
  private ArrayList<Action> actions;
}
