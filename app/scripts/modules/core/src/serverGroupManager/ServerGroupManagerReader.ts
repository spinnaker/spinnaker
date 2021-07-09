import { REST } from '../api/ApiService';

import { IServerGroupManager } from '../domain/IServerGroupManager';

export class ServerGroupManagerReader {
  public static getServerGroupManagersForApplication(application: string): PromiseLike<IServerGroupManager[]> {
    return REST('/applications').path(application, 'serverGroupManagers').get();
  }
}
