package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.artifacts.getScmBaseLink
import com.slack.api.model.kotlin_extension.block.SectionBlockBuilder
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Constructing a git related data block, which will be a part of a slack notification
 */
@Component
class GitDataGenerator(
  private val scmInfo: ScmInfo,
  @Value("\${spinnaker.baseUrl}") private val spinnakerBaseUrl: String
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun generateStashRepoLink(gitMetadata: GitMetadata): String {
      val baseScmUrl = gitMetadata.commitInfo?.link?.let { getScmBaseLink(scmInfo, it) }
      return "$baseScmUrl/projects/${gitMetadata.project}/repos/${gitMetadata.repo?.name}"
  }

  /**
   * generateScmInfo will create a slack section blocks, which looks like:
   * "spkr/keel › PR#7 › master › c25a357"
   * Or: "spkr/keel › master › c25a358" (if it's a commit without a PR)
   * Each component will have the corresponding link attached to SCM
   */
  fun generateScmInfo(sectionBlockBuilder: SectionBlockBuilder, application: String, artifact: PublishedArtifact): SectionBlockBuilder {
    with(sectionBlockBuilder) {
      var details = ""
      val artifactUrl = generateArtifactUrl(application, artifact.reference, artifact.version)

        with(artifact.gitMetadata) {
          if (this != null) {
            val repoLink = generateStashRepoLink(this)

            if (project != null && repo != null && branch != null) {
              details += "<$repoLink|$project/" +
                "${repo!!.name}> › " +
                "<$repoLink/branches|$branch> › "
            }
            //if the commit is not a part of a PR, don't include PR's data
            if (Strings.isNotEmpty(pullRequest?.number)) {
              details += "<${pullRequest?.url}|PR#${pullRequest?.number}> › "
            }

            if (commitInfo != null && commitInfo!!.sha != null && commitInfo!!.sha?.length!! >= 7) {
              markdownText(details +
                "<${commitInfo?.link}|${commitInfo?.sha?.substring(0, 7)}>")
            }
          } else {
            log.debug("git metadata is empty for application $application")
          }
        }

      accessory {
        button {
          text("More...")
          // action id will be consisted by 3 sections with ":" between them to keep it consistent
          actionId("button:url:more")
          url(artifactUrl)
        }
      }
      return this
    }
  }

  /**
   * generateCommitInfo will create a slack section blocks, which looks like:
   * "App: keel
   *  Version: #36 by @emburns
      Environment:  TESTING
              Update README.md"
   * Or (if [olderVersion] exists):
   *      "Version: #93 → #94 by @msrc
          Environment: TEST"
   */
  fun generateCommitInfo(sectionBlockBuilder: SectionBlockBuilder,
                         application: String,
                         imageUrl: String,
                         artifact: PublishedArtifact,
                         altText: String,
                         olderVersion: String?  = null,
                         env: String? = null): SectionBlockBuilder {
    var details = ""
    if (olderVersion != null && olderVersion.isNotEmpty()) {
      details += "~$olderVersion~ →"
    }
    var envDetails = ""
    if (env != null) {
      envDetails +=  "*Environment:* $env\n\n "
    }

    val artifactUrl = generateArtifactUrl(application, artifact.reference, artifact.version)
    with(sectionBlockBuilder) {
      with(artifact) {
        if (buildMetadata != null && gitMetadata != null && gitMetadata!!.commitInfo != null) {
          markdownText("*App:* $application\n" +
            "*Version:* $details <$artifactUrl|#${buildMetadata!!.number}> " +
            "by @${gitMetadata!!.author}\n " + envDetails +
            "${gitMetadata!!.commitInfo?.message}")

          accessory {
            image(imageUrl = imageUrl, altText = altText)
          }
        }
      }
      return this
    }
  }

  /**
   * generateUnpinCommitInfo will create a slack section blocks, which looks like:
   * "App: keel
   *  Environment:  TESTING
   *  V PREVIOUSLY PINNED V        (V is a down arrow emoji)
   *  Version: #36 by @emburns
   *    Update README.md"
   */
  fun generateUnpinCommitInfo(sectionBlockBuilder: SectionBlockBuilder,
                         application: String,
                         imageUrl: String,
                         artifact: PublishedArtifact,
                         altText: String,
                         env: String): SectionBlockBuilder {
    val artifactUrl = generateArtifactUrl(application, artifact.reference, artifact.version)
    with(sectionBlockBuilder) {
      with(artifact) {
        if (buildMetadata != null && gitMetadata != null && gitMetadata!!.commitInfo != null) {
          markdownText("*App:* $application\n" +
            "*Environment:* $env\n\n " +
            ":arrow_down: *PREVIOUSLY PINNED* :arrow_down:\n" +
            "*Version:* <$artifactUrl|#${buildMetadata!!.number}> " +
            "by @${gitMetadata!!.author}\n\n" +
            "${gitMetadata!!.commitInfo?.message}")

          accessory {
            image(imageUrl = imageUrl, altText = altText)
          }
        }
      }
      return this
    }
  }

  fun generateArtifactUrl(application: String, reference: String, version: String) =
    "$spinnakerBaseUrl/#/applications/${application}/environments/${reference}/${version}"

  fun generateBuildNumberLink(application: String, reference: String, version: String, buildMetadata: BuildMetadata) =
    "<${generateArtifactUrl(application, reference, version)}|#${buildMetadata!!.number}>"
}
