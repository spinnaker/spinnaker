import { module, IPromise } from 'angular';

import { API } from 'core/api/ApiService';

import { IServerGroupManager } from 'core/domain/IServerGroupManager';

export class ServerGroupManagerService {
  public getServerGroupManagersForApplication(application: string): IPromise<IServerGroupManager[]> {
    return API.one('applications')
      .one(application)
      .one('serverGroupManagers')
      .get();
  }
}

export const SERVER_GROUP_MANAGER_SERVICE = 'spinnaker.core.serverGroupManager.service';
module(SERVER_GROUP_MANAGER_SERVICE, []).service('serverGroupManagerService', ServerGroupManagerService);
