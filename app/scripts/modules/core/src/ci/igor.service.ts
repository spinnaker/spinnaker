import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { API } from 'core/api/ApiService';
import { IBuild, IJobConfig, IGcbTrigger } from 'core/domain';

export enum BuildServiceType {
  Jenkins = 'jenkins',
  Travis = 'travis',
  Wercker = 'wercker',
  Concourse = 'concourse',
}

export class IgorService {
  public static listMasters(buildType: BuildServiceType = null): IPromise<string[]> {
    const allMasters: IPromise<string[]> = API.one('v2')
      .one('builds')
      .withParams({ type: buildType })
      .get();
    if (!allMasters) {
      return $q.reject('An error occurred when retrieving build masters');
    }
    switch (buildType) {
      case BuildServiceType.Jenkins:
        return allMasters.then(masters => masters.filter(master => !/^travis-/.test(master)));
      case BuildServiceType.Travis:
        return allMasters.then(masters => masters.filter(master => /^travis-/.test(master)));
      case BuildServiceType.Concourse:
        return allMasters.then(masters => masters.filter(master => /^concourse-/.test(master)));
      default:
        return allMasters;
    }
  }

  public static listJobsForMaster(master: string): IPromise<string[]> {
    return API.one('v2')
      .one('builds')
      .one(master)
      .one('jobs')
      .get();
  }

  public static listBuildsForJob(master: string, job: string): IPromise<IBuild[]> {
    return API.one('v2')
      .one('builds')
      .one(master)
      .one('builds')
      .one(job)
      .get();
  }

  public static getJobConfig(master: string, job: string): IPromise<IJobConfig> {
    return API.one('v2')
      .one('builds')
      .one(master)
      .one('jobs')
      .one(job)
      .get();
  }

  public static getGcbAccounts(): IPromise<string[]> {
    return API.one('gcb')
      .one('accounts')
      .get();
  }

  public static getGcbTriggers(account: string): IPromise<IGcbTrigger[]> {
    return API.one('gcb')
      .one('triggers')
      .one(account)
      .get();
  }

  public static getCodeBuildAccounts(): IPromise<string[]> {
    return API.one('codebuild')
      .one('accounts')
      .get();
  }
}
