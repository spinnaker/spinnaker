import { module, IPromise } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';

import { IServerGroupManager } from 'core/domain/IServerGroupManager';

export class ServerGroupManagerService {
  constructor(private API: Api) {
    'ngInject';
  }

  public getServerGroupManagersForApplication(application: string): IPromise<IServerGroupManager[]> {
    return this.API.one('applications').one(application).one('serverGroupManagers').get();
  }
}

export const SERVER_GROUP_MANAGER_SERVICE = 'spinnaker.core.serverGroupManager.service';
module(SERVER_GROUP_MANAGER_SERVICE, [API_SERVICE])
  .service('serverGroupManagerService', ServerGroupManagerService);
