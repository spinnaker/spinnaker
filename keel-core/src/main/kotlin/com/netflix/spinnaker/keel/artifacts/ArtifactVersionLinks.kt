package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.exceptions.UnsupportedScmType
import kotlinx.coroutines.runBlocking

//Comparing 2 versions of a specific artifact, and generate a SCM comparable link based on old vs. new version
fun generateCompareLink(scmInfo: ScmInfo, version1: PublishedArtifact?, version2: PublishedArtifact?, artifact: DeliveryArtifact): String? {
  return if (version1 != null && version2 != null) {
    return if (artifact.sortingStrategy.comparator.compare(version1, version2) > 0) { //these comparators sort in dec order, so condition is flipped
      //version2 is newer than version1
      generateCompareLink(scmInfo, version2.gitMetadata, version1.gitMetadata)
    } else {
      //version2 is older than version1
      generateCompareLink(scmInfo, version1.gitMetadata, version2.gitMetadata)
    }
  } else {
    null
  }
}

//Generating a SCM compare link between source (new version) and target (old version) versions (the order matter!)
private fun generateCompareLink(scmInfo: ScmInfo, newerGitMetadata: GitMetadata?, olderGitMetadata: GitMetadata?): String? {
  val baseScmUrl = newerGitMetadata?.commitInfo?.link?.let { getScmBaseLink(scmInfo, it) }
  return if (baseScmUrl != null && olderGitMetadata != null && !(olderGitMetadata.commitInfo?.sha.isNullOrEmpty())) {
    "$baseScmUrl/projects/${newerGitMetadata.project}/repos/${newerGitMetadata.repo?.name}/compare/commits?" +
      "targetBranch=${olderGitMetadata.commitInfo?.sha}&sourceBranch=${newerGitMetadata.commitInfo?.sha}"
  } else {
    null
  }
}

//Calling igor to fetch all base urls by SCM type, and returning the right one based on current commit link
fun getScmBaseLink(scmInfo: ScmInfo, commitLink: String): String? {
  val scmBaseURLs = runBlocking {
    scmInfo.getScmInfo()
  }
  when {
    "stash" in commitLink ->
      return scmBaseURLs["stash"]
    else ->
      throw UnsupportedScmType(message = "Stash is currently the only supported SCM type")
  }
}
