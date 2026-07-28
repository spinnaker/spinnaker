import { REST } from '../api/ApiService';
import type { IBuild, IGcbTrigger, IJobConfig } from '../domain';

export enum BuildServiceType {
  Jenkins = 'jenkins',
  Travis = 'travis',
  Concourse = 'concourse',
}

export class IgorService {
  public static listMasters(buildType: BuildServiceType = null): Promise<string[]> {
    const allMasters: Promise<string[]> = REST('/v2/builds').query({ type: buildType }).get();
    if (!allMasters) {
      return Promise.reject('An error occurred when retrieving build masters');
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

  public static listJobsForMaster(master: string): Promise<string[]> {
    return REST('/v3/builds').path(master, 'jobs').get();
  }

  public static listBuildsForJob(master: string, job: string): Promise<IBuild[]> {
    return REST('/v3/builds').path(master, 'builds').query({ job }).get();
  }

  public static getJobConfig(master: string, job: string): Promise<IJobConfig> {
    return REST('/v3/builds').path(master, 'job').query({ job }).get();
  }

  public static getGcbAccounts(): Promise<string[]> {
    return REST('/gcb/accounts').get();
  }

  public static getGcbTriggers(account: string): Promise<IGcbTrigger[]> {
    return REST('/gcb/triggers').path(account).get();
  }

  public static getCodeBuildAccounts(): Promise<string[]> {
    return REST('/codebuild/accounts').get();
  }

  public static getCodeBuildProjects(account: string): Promise<string[]> {
    return REST('/codebuild/projects').path(account).get();
  }
}
