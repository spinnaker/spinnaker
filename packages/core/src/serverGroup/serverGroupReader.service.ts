import { $log } from 'ngimport';

import { REST } from '../api/ApiService';
import { IServerGroup } from '../domain';

export class ServerGroupReader {
  public static getScalingActivities(serverGroup: IServerGroup): PromiseLike<any[]> {
    return REST('/applications')
      .path(
        serverGroup.app,
        'clusters',
        serverGroup.account,
        serverGroup.cluster,
        'serverGroups',
        serverGroup.name,
        'scalingActivities',
      )
      .query({
        region: serverGroup.region,
        provider: serverGroup.cloudProvider,
      })
      .get()
      .catch((error: any): any[] => {
        $log.error(error, 'error retrieving scaling activities');
        return [];
      });
  }

  public static getServerGroup(
    application: any,
    account: string,
    region: string,
    serverGroupName: string,
  ): PromiseLike<IServerGroup> {
    return REST('/applications')
      .path(application, 'serverGroups', account, region, serverGroupName)
      .query({ includeDetails: 'false' })
      .get();
  }
}
