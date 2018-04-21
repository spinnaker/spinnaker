import { IPromise, module, IQService } from 'angular';

import { API } from 'core/api/ApiService';
import { IBuild, IJobConfig } from 'core/domain';

export enum BuildServiceType {
  Jenkins,
  Travis,
}

export class IgorService {
  constructor(private $q: IQService) {
    'ngInject';
  }

  public listMasters(type: BuildServiceType = null): IPromise<string[]> {
    const allMasters: IPromise<string[]> = API.one('v2')
      .one('builds')
      .get();
    if (!allMasters) {
      return this.$q.reject('An error occurred when retrieving build masters');
    }
    switch (type) {
      case BuildServiceType.Jenkins:
        return allMasters.then(masters => masters.filter(master => !/^travis-/.test(master)));
      case BuildServiceType.Travis:
        return allMasters.then(masters => masters.filter(master => /^travis-/.test(master)));
      default:
        return allMasters;
    }
  }

  public listJobsForMaster(master: string): IPromise<string[]> {
    return API.one('v2')
      .one('builds')
      .one(master)
      .one('jobs')
      .get();
  }

  public listBuildsForJob(master: string, job: string): IPromise<IBuild[]> {
    return API.one('v2')
      .one('builds')
      .one(master)
      .one('builds')
      .one(job)
      .get();
  }

  public getJobConfig(master: string, job: string): IPromise<IJobConfig> {
    return API.one('v2')
      .one('builds')
      .one(master)
      .one('jobs')
      .one(job)
      .get();
  }
}

export const IGOR_SERVICE = 'spinnaker.core.ci.jenkins.igor.service';
module(IGOR_SERVICE, []).factory('igorService', ($q: IQService) => new IgorService($q));
