package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.slack.SlackService
import com.slack.api.model.kotlin_extension.block.SectionBlockBuilder
import com.slack.api.model.kotlin_extension.block.dsl.LayoutBlockDsl
import com.slack.api.model.kotlin_extension.view.blocks
import com.slack.api.model.view.View
import com.slack.api.model.view.Views.view
import com.slack.api.model.view.Views.viewClose
import com.slack.api.model.view.Views.viewTitle
import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Constructing a git related data block, which will be a part of a slack notification
 */
@Component
@EnableConfigurationProperties(BaseUrlConfig::class)
class GitDataGenerator(
  private val scmInfo: ScmInfo,
  val config: BaseUrlConfig,
  val slackService: SlackService,
  private val artifactVersionLinks: ArtifactVersionLinks,
) {

  companion object {
    const val GIT_COMMIT_MESSAGE_LENGTH = 100
    const val EMPTY_COMMIT_TEXT = "_No commit message to display_"
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private fun generateStashRepoLink(gitMetadata: GitMetadata): String {
      val baseScmUrl = gitMetadata.commitInfo?.link?.let { artifactVersionLinks.getScmBaseLink(it) }
      return "$baseScmUrl/projects/${gitMetadata.project}/repos/${gitMetadata.repo?.name}"
  }

  fun generateConfigUrl(application: String): String =
    "${config.baseApiUrl}/managed/application/$application/config"

  /**
   * Builds a modal with the full commit message
   */
  fun buildFullCommitModal(message: String, hash: String): View {
    return view { thisView -> thisView
      .callbackId("view:$hash:modal")
      .type("modal")
      .notifyOnClose(false)
      .title(viewTitle { it.type("plain_text").text("Full commit").emoji(true) })
      .close(viewClose { it.type("plain_text").text("Close").emoji(true) })
      .blocks {
        section {
          blockId("commit-message")
          markdownText(message)
        }
      }
    }
  }

  /**
   * Adds a "Show full commit" button if the commit message is > [GIT_COMMIT_MESSAGE_LENGTH].
   * Doesn't do anything if there is no commit message or the commit message is not too long.
   */
  fun conditionallyAddFullCommitMsgButton(layoutBlockDsl: LayoutBlockDsl, gitMetadata: GitMetadata) {
    val commitMessage = gitMetadata.commitInfo?.message ?: EMPTY_COMMIT_TEXT
    val hash = gitMetadata.commitInfo?.sha ?: "no-hash"
    if (commitMessage.length > GIT_COMMIT_MESSAGE_LENGTH) {
      layoutBlockDsl.actions {
        elements {
          button {
            text("Show full commit")
            // action id will be consisted by 3 sections with ":" between them to keep it consistent
            actionId("button:$hash:FULL_COMMIT_MODAL")
            // using the value to sneakily pass what we want to display in the modal, limit 2000 chars
            value(commitMessage.take(2000))
          }
        }
      }
    }
  }

  fun formatCommitMessage(gitMetadata: GitMetadata?): String {
    val message = gitMetadata?.commitInfo?.message ?: EMPTY_COMMIT_TEXT
    return if (message.length > GIT_COMMIT_MESSAGE_LENGTH) {
      message.take(GIT_COMMIT_MESSAGE_LENGTH) + "..."
    } else {
      message
    }
  }

  /**
   * generateScmInfo will create a slack section blocks, which looks like:
   * "spkr/keel › PR#7 › master › c25a357"
   * Or: "spkr/keel › master › c25a358" (if it's a commit without a PR)
   * Each component will have the corresponding link attached to SCM
   */
  fun generateScmInfo(
    sectionBlockBuilder: SectionBlockBuilder,
    application: String,
    gitMetadata: GitMetadata,
    artifact: PublishedArtifact?
  ): SectionBlockBuilder {
    with(sectionBlockBuilder) {
      var details = ""
      with(gitMetadata) {
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
      }
      if (artifact != null) {
        val artifactUrl = generateArtifactUrl(application, artifact.reference, artifact.version)
        accessory {
          button {
            text("More...")
            // action id will be consisted by 3 sections with ":" between them to keep it consistent
            actionId("button:url:more")
            url(artifactUrl)
          }
        }
      }
      return this
    }
  }

  /**
   * generateCommitInfo will create a slack section blocks, which looks like:
   * "App: keel
   *  Version: #36 by @emburns
   *  Environment:  TESTING
   *          Update README.md"
   * Or (if [olderVersion] exists):
   *      "Version: #93 → #94 by @msrc
   *      Environment: TEST"
   */
  fun generateCommitInfo(sectionBlockBuilder: SectionBlockBuilder,
                         application: String,
                         imageUrl: String,
                         artifact: PublishedArtifact,
                         altText: String,
                         olderVersion: String?  = null,
                         env: String? = null
  ): SectionBlockBuilder {
    var envDetails = ""
    if (env != null) {
      envDetails +=  "*Environment:* $env\n "
    }

    var text = "*App:* $application\n $envDetails"

    with(sectionBlockBuilder) {
      text += generateVersionMarkdown(application, artifact.reference, artifact, olderVersion)
      text += "\n\n ${formatCommitMessage(artifact.gitMetadata)}"
      markdownText(text)
      accessory {
        image(imageUrl = imageUrl, altText = altText)
      }
      return this
    }
  }

  fun generateVersionMarkdown(
    application: String,
    reference: String,
    artifact: PublishedArtifact,
    olderVersion: String?,
  ): String {
    with(artifact) {
      var details = ""
      if (olderVersion != null && olderVersion.isNotEmpty()) {
        details += "~$olderVersion~ →"
      }
      val url = generateArtifactUrl(application, reference, artifact.version)
      var text = ""
      if (buildMetadata == null && gitMetadata == null) {
        // fall back to info on the artifact
        text += "*Version:* ${artifact.version} for artifact reference ${artifact.reference}"
      }

      if (buildMetadata?.number != null) {
        text += "*Version:* $details <$url|#${buildMetadata?.number}> "
      }

      if (gitMetadata?.commitInfo != null) {
        val author = gitMetadata?.author
        if (author != null) {
          val username = slackService.getUsernameByEmailPrefix(author)
          text += "by $username"
        }
      }
      return text
    }
  }

  /**
   * This is for generating delivery config commit updates.
   *
   * generateCommitInfo will create a slack section blocks, which looks like:
   * "App: keel
   *  Commit by @emburns
   *   Update README.md"
   *
   */
  fun generateCommitInfoNoArtifact(sectionBlockBuilder: SectionBlockBuilder,
                                   application: String,
                                   imageUrl: String,
                                   altText: String,
                                   gitMetadata: GitMetadata,
                                   username: String?): SectionBlockBuilder {

    with(sectionBlockBuilder) {
      markdownText("*App:* $application\n" +
        "Commit by $username\n " +
        formatCommitMessage(gitMetadata))

      accessory {
        image(imageUrl = imageUrl, altText = altText)
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
                         env: String,
                         username: String? = null
  ): SectionBlockBuilder {
    val artifactUrl = generateArtifactUrl(application, artifact.reference, artifact.version)
    var text = "*App:* $application\n*Environment:* $env\n\n"
    with(sectionBlockBuilder) {
      with(artifact) {
        if (buildMetadata == null && gitMetadata == null) {
          // fall back to info on the artifact
          text += "*Version:* ${artifact.version} for artifact reference ${artifact.reference}"
        }

        if (buildMetadata?.number != null) {
          text += ":arrow_down: *PREVIOUSLY PINNED* :arrow_down:\n" +
            "*Version:* <$artifactUrl|#${buildMetadata?.number}> "
        }

        if (gitMetadata?.commitInfo != null && username != null) {
          val author = gitMetadata?.author
          if (author != null) {
            val username = slackService.getUsernameByEmailPrefix(author)
            text += "by $username"
          }
          text += "\n\n${formatCommitMessage(gitMetadata)}"
        }


        markdownText(text)
        accessory {
          image(imageUrl = imageUrl, altText = altText)
        }
      }
      return this
    }
  }

  fun generateArtifactUrl(application: String, reference: String, version: String) =
    "${config.baseUrl}/#/applications/${application}/environments/${reference}/${version}"
}
