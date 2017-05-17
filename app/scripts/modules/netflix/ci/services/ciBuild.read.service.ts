import { IHttpPromiseCallbackArg, IPromise, module } from 'angular';

import {
  Api,
  API_SERVICE,
  ORCHESTRATED_ITEM_TRANSFORMER,
  OrchestratedItemTransformer,
  SETTINGS
} from '@spinnaker/core';

import { CiFilterModel } from '../ciFilter.model';

interface ICiBuildChangeRef {
  id: string;
  displayId: string;
}

interface ICiBuildChangeTargetAuthor {
  email: string;
  name: string;
  username: string;
}

interface ICiBuildChangeTarget extends ICiBuildChangeRef {
  author: ICiBuildChangeTargetAuthor;
  message: string;
}

interface ICiBuildChange {
  from: ICiBuildChangeTarget;
  ref: ICiBuildChangeRef;
  to: ICiBuildChangeTarget;
}

export interface ICiBuild {
  buildNumber: number;
  change: ICiBuildChange;
  completedAt: number;
  completionStatus: string;
  endTime?: number;
  id: string;
  isRunning?: boolean;
  repositoryId: string;
  runningTimeInMs?: number;
  startedAt: number;
  startTime?: number;
}

export interface ICiBuildOutputConfig {
  data: string[];
  last: boolean;
  nextStart: string;
}

export class CiBuildReader {

  public static get MAX_LINES(): number {
    return 4095;
  };

  constructor(private API: Api,
              private orchestratedItemTransformer: OrchestratedItemTransformer) {
    'ngInject';
  }

  private builds(): any {
    return this.API.all('ci').all('builds');
  }

  private transformBuild(build: ICiBuild): ICiBuild {
    build.startTime = build.startedAt;
    build.endTime = build.completedAt;
    build.isRunning = build.completionStatus === 'INCOMPLETE';
    this.orchestratedItemTransformer.addRunningTime(build);

    return build;
  }

  public getBuilds(repoType: string, projectKey: string, repoSlug: string): IPromise<ICiBuild[]> {
    return this.builds()
      .get({repoType, projectKey, repoSlug, filter: CiFilterModel.searchFilter})
      .then((response: IHttpPromiseCallbackArg<ICiBuild[]>) => {
        return response.data.map((build: ICiBuild) => this.transformBuild(build));
      });
  }

  public getRunningBuilds(repoType: string, projectKey: string, repoSlug: string): IPromise<ICiBuild[]> {
    return this.builds()
      .get({repoType, projectKey, repoSlug, completionStatus: 'INCOMPLETE'})
      .then((response: IHttpPromiseCallbackArg<ICiBuild[]>) => {
        return response.data.map((build: ICiBuild) => this.transformBuild(build));
      });
  }

  public getBuildDetails(buildId: string): IPromise<ICiBuild> {
    return this.builds().one(buildId).get().then((build: ICiBuild) => {
      return this.transformBuild(build);
    });
  }

  public getBuildOutput(buildId: string, start = -1): IPromise<ICiBuildOutputConfig> {
    return this.builds().one(buildId).one('output').get({start, limit: CiBuildReader.MAX_LINES});
  }

  public getBuildConfig(buildId: string): IPromise<ICiBuildOutputConfig> {
    return this.builds().one(buildId).one('config').get();
  }

  public getBuildRawLogLink(buildId: string): string {
    return [SETTINGS.gateUrl, 'ci', 'builds', buildId, 'rawOutput'].join('/');
  }
}

export const CI_BUILD_READ_SERVICE = 'spinnaker.netflix.ci.build.read.service';
module(CI_BUILD_READ_SERVICE, [
  API_SERVICE,
  ORCHESTRATED_ITEM_TRANSFORMER,
])
  .service('ciBuildReader', CiBuildReader);
