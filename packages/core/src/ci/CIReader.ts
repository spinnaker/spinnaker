import { REST } from '../api';
import { ICiBuild, ICiBuildAPI, ICiBuildOutputConfig } from './domain';
import { OrchestratedItemTransformer } from '../orchestratedItem';

function transformResultStatus(result: string): string {
  switch (result) {
    case 'SUCCESS':
      return 'SUCCEEDED';
    case 'FAILURE':
      return 'FAILED';
    case 'BUILDING':
      return 'INCOMPLETE';
    case 'ABORTED':
      return 'ABORTED';
  }
  return result;
}

function transformBuildToUIFormat(build: ICiBuildAPI) {
  const { artifacts, properties, scm } = build;
  const startTime = parseInt(properties.startedTs, 10);

  const transformedBuild = {
    author: scm.length == 0 ? '' : scm[0].committer,
    artifacts: artifacts.map((artifact) => ({ name: artifact.fileName, url: artifact.displayPath })),
    branchName: scm.length == 0 ? '' : scm[0].branch,
    commitId: scm.length == 0 ? '' : scm[0].sha1.substr(0, 7), // naive shortening of the commit hash.
    commitLink: scm.length == 0 ? '' : scm[0].compareUrl,
    commitMessage: scm.length == 0 ? '' : scm[0].message,
    duration: build.duration,
    fullDisplayName: build.fullDisplayName,
    id: build.id,
    isRunning: build.building,
    number: build.number,
    projectKey: properties.projectKey,
    pullRequestNumber: properties.pullRequestNumber,
    pullRequestUrl: properties.pullRequestUrl,
    repoSlug: properties.repoSlug,
    result: transformResultStatus(build.result),
    startTime,
    url: build.url,
  };

  OrchestratedItemTransformer.addRunningTime(transformedBuild);
  return transformedBuild;
}

export class CIReader {
  public static get MAX_LINES(): number {
    return 4095;
  }

  public static getBuilds(repoType: string, projectKey: string, repoSlug: string): PromiseLike<ICiBuild[]> {
    return REST('ci/builds')
      .query({ repoType, projectKey, repoSlug })
      .get()
      .then((builds: ICiBuildAPI[]) => builds.map((build: ICiBuildAPI) => transformBuildToUIFormat(build)));
  }

  public static getRunningBuilds(repoType: string, projectKey: string, repoSlug: string): PromiseLike<ICiBuild[]> {
    return REST('ci/builds').query({ repoType, projectKey, repoSlug, completionStatus: 'INCOMPLETE' }).get();
  }

  public static getBuildOutput(buildId: string, start = -1): PromiseLike<ICiBuildOutputConfig> {
    return REST('ci/builds').path(buildId, 'output').query({ start, limit: CIReader.MAX_LINES }).get();
  }
}
