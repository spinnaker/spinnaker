
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
  public static listMasters(buildType: BuildServiceType = null): PromiseLike<string[]> {
    const allMasters: PromiseLike<string[]> = API.one('v2').one('builds').withParams({ type: buildType }).get();
    if (!allMasters) {
      return $q.reject('An error occurred when retrieving build masters');
    }
    switch (buildType) {
      case BuildServiceType.Jenkins:
        return allMasters.then((masters) => masters.filter((master) => !/^travis-/.test(master)));
      case BuildServiceType.Travis:
        return allMasters.then((masters) => masters.filter((master) => /^travis-/.test(master)));
      case BuildServiceType.Concourse:
        return allMasters.then((masters) => masters.filter((master) => /^concourse-/.test(master)));
      default:
        return allMasters;
    }
  }

  public static listJobsForMaster(master: string): PromiseLike<string[]> {
    return API.one('v2').one('builds').one(master).one('jobs').get();
  }

  public static listBuildsForJob(master: string, job: string): PromiseLike<IBuild[]> {
    return API.one('v2').one('builds').one(master).one('builds').one(job).get();
  }

  public static getJobConfig(master: string, job: string): PromiseLike<IJobConfig> {
    return API.one('v2').one('builds').one(master).one('jobs').one(job).get();
  }

  public static getGcbAccounts(): PromiseLike<string[]> {
    return API.one('gcb').one('accounts').get();
  }

  public static getGcbTriggers(account: string): PromiseLike<IGcbTrigger[]> {
    return API.one('gcb').one('triggers').one(account).get();
  }

  public static getCodeBuildAccounts(): PromiseLike<string[]> {
    return API.one('codebuild').one('accounts').get();
  }

  public static getCodeBuildProjects(account: string): PromiseLike<string[]> {
    return API.one('codebuild').one('projects').one(account).get();
  }
}
