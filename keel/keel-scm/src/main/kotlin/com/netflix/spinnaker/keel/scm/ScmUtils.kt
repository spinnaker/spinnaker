package com.netflix.spinnaker.keel.scm

import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.igor.getDefaultBranch
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

@Component
class ScmUtils(
  private val cacheFactory: CacheFactory,
  private val scmService: ScmService,
) {
  private val cache = cacheFactory.asyncBulkLoadingCache("scmInfo") {
    scmService.getScmInfo()
  }

  fun getBranchLink(repoType: String?, repoProjectKey: String?, repoSlug: String?, branch: String?): String? {
    if (repoType == null || repoProjectKey == null || repoSlug == null || branch == null) {
      return null
    }

    val scmBaseUrl = getScmBaseLink(repoType) ?: return null

    return when (repoType) {
      "stash" -> "$scmBaseUrl/projects/$repoProjectKey/repos/$repoSlug/browse?at=refs/heads/$branch"
      "github" -> "$scmBaseUrl/$repoProjectKey/$repoSlug/tree/$branch"
      else -> null
    }
  }

  fun getCommitLink(event: CodeEvent): String? {
    return getCommitLink(event.repoType, event.projectKey, event.repoSlug, event.commitHash)
  }

  fun getCommitLink(repoType: String?, repoProjectKey: String?, repoSlug: String?, commitHash: String?): String? {
    if (repoType == null || repoProjectKey == null || repoSlug == null || commitHash == null) {
      return null
    }
    val scmBaseUrl = getScmBaseLink(repoType) ?: return null

    return when (repoType) {
      "stash" -> "$scmBaseUrl/projects/$repoProjectKey/repos/$repoSlug/commits/$commitHash"
      "github" -> "$scmBaseUrl/$repoProjectKey/$repoSlug/commit/$commitHash"
      else -> null
    }
  }

  fun getPullRequestLink(event: PrEvent): String? {
    return getPullRequestLink(event.repoType, event.projectKey, event.repoSlug, event.pullRequestId)
  }

  fun getPullRequestLink(repoType: String?, repoProjectKey: String?, repoSlug: String?, pullRequestId: String?): String? {
    if (repoType == null || repoProjectKey == null || repoSlug == null || pullRequestId == null) {
      return null
    }
    val scmBaseUrl = getScmBaseLink(repoType) ?: return null
    return when (repoType) {
      "stash" -> "$scmBaseUrl/projects/$repoProjectKey/repos/$repoSlug/pull-requests/$pullRequestId"
      "github" -> "$scmBaseUrl/$repoProjectKey/$repoSlug/pull/$pullRequestId"
      else -> null
    }
  }

  fun getScmBaseLink(repoType: String): String? = runBlocking {
    cache.get(repoType).await()
  }

  fun getDefaultBranch(application: Application): String {
    return application.getDefaultBranch(scmService)
  }

}
