import { IPromise } from 'angular';

import { $log } from 'ngimport';

import { API } from 'core/api/ApiService';
import { IServerGroup } from 'core/domain';

export class ServerGroupReader {
  public static getScalingActivities(serverGroup: IServerGroup): IPromise<any[]> {
    return API.one('applications')
      .one(serverGroup.app)
      .all('clusters')
      .all(serverGroup.account)
      .all(serverGroup.cluster)
      .one('serverGroups', serverGroup.name)
      .all('scalingActivities')
      .withParams({
        region: serverGroup.region,
        provider: serverGroup.cloudProvider,
      })
      .getList()
      .catch(
        (error: any): any[] => {
          $log.error(error, 'error retrieving scaling activities');
          return [];
        },
      );
  }

  public static getServerGroup(
    application: any,
    account: string,
    region: string,
    serverGroupName: string,
  ): IPromise<IServerGroup> {
    return API.one('applications')
      .one(application)
      .all('serverGroups')
      .all(account)
      .all(region)
      .one(serverGroupName)
      .withParams({ includeDetails: 'false' })
      .get();
  }
}
