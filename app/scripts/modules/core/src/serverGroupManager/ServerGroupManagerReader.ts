import { REST } from 'core/api/ApiService';

import { IServerGroupManager } from 'core/domain/IServerGroupManager';

export class ServerGroupManagerReader {
  public static getServerGroupManagersForApplication(application: string): PromiseLike<IServerGroupManager[]> {
    return REST('/applications').path(application, 'serverGroupManagers').get();
  }
}
