import { IPromise } from 'angular';
import { $log } from 'ngimport';

import { API, IServerGroup } from '@spinnaker/core';

export interface IEventDescription {
  createdAt: number;
  message: string;
  id: string;
  status: string;
}

export class ServerGroupEventsReader {
  public static getEvents(serverGroup: IServerGroup): IPromise<IEventDescription[]> {
    return API.one('applications')
      .one(serverGroup.app)
      .one('serverGroups')
      .all(serverGroup.account)
      .one(serverGroup.name)
      .all('events')
      .withParams({
        region: serverGroup.region,
        provider: serverGroup.cloudProvider,
      })
      .getList()
      .catch(
        (error: any): any[] => {
          $log.error(error, 'error retrieving events');
          return [];
        },
      );
  }
}
