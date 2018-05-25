import { IPromise } from 'angular';

import { API } from 'core/api/ApiService';

import { IServerGroupManager } from 'core/domain/IServerGroupManager';

export class ServerGroupManagerReader {
  public static getServerGroupManagersForApplication(application: string): IPromise<IServerGroupManager[]> {
    return API.one('applications')
      .one(application)
      .one('serverGroupManagers')
      .get();
  }
}
