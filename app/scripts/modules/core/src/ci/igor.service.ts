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
    const allMasters: PromiseLike<string[]> = API.path('v2').path('builds').query({ type: buildType }).get();
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
    return API.path('v2').path('builds').path(master).path('jobs').get();
  }

  public static listBuildsForJob(master: string, job: string): PromiseLike<IBuild[]> {
    return API.path('v2').path('builds').path(master).path('builds').path(job).get();
  }

  public static getJobConfig(master: string, job: string): PromiseLike<IJobConfig> {
    return API.path('v2').path('builds').path(master).path('jobs').path(job).get();
  }

  public static getGcbAccounts(): PromiseLike<string[]> {
    return API.path('gcb').path('accounts').get();
  }

  public static getGcbTriggers(account: string): PromiseLike<IGcbTrigger[]> {
    return API.path('gcb').path('triggers').path(account).get();
  }

  public static getCodeBuildAccounts(): PromiseLike<string[]> {
    return API.path('codebuild').path('accounts').get();
  }

  public static getCodeBuildProjects(account: string): PromiseLike<string[]> {
    return API.path('codebuild').path('projects').path(account).get();
  }
}
