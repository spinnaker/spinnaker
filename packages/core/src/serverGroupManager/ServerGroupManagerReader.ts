import { REST } from '../api/ApiService';

import type { IServerGroupManager } from '../domain/IServerGroupManager';

export class ServerGroupManagerReader {
  public static getServerGroupManagersForApplication(application: string): PromiseLike<IServerGroupManager[]> {
    return REST('/applications').path(application, 'serverGroupManagers').get();
  }
}
