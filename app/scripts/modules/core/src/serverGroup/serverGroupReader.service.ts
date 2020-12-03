import { $log } from 'ngimport';

import { API } from 'core/api/ApiService';
import { IServerGroup } from 'core/domain';

export class ServerGroupReader {
  public static getScalingActivities(serverGroup: IServerGroup): PromiseLike<any[]> {
    return API.path('applications')
      .path(serverGroup.app)
      .path('clusters')
      .path(serverGroup.account)
      .path(serverGroup.cluster)
      .path('serverGroups', serverGroup.name)
      .path('scalingActivities')
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
    return API.path('applications')
      .path(application)
      .path('serverGroups')
      .path(account)
      .path(region)
      .path(serverGroupName)
      .query({ includeDetails: 'false' })
      .get();
  }
}
