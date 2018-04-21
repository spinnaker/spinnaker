import { ILogService, IPromise, module } from 'angular';

import { API } from 'core/api/ApiService';
import { IServerGroup } from 'core/domain';

export class ServerGroupReader {
  constructor(private $log: ILogService) {
    'ngInject';
  }

  public getScalingActivities(serverGroup: IServerGroup): IPromise<any[]> {
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
      .catch((error: any): any[] => {
        this.$log.error(error, 'error retrieving scaling activities');
        return [];
      });
  }

  public getServerGroup(
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
      .get();
  }
}

export const SERVER_GROUP_READER = 'spinnaker.core.serverGroup.read.service';
module(SERVER_GROUP_READER, []).service('serverGroupReader', ServerGroupReader);
