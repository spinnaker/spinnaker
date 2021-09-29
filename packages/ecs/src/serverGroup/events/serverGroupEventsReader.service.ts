import { $log } from 'ngimport';

import type { IServerGroup } from '@spinnaker/core';
import { REST } from '@spinnaker/core';

export interface IEventDescription {
  createdAt: number;
  message: string;
  id: string;
  status: string;
}

export class ServerGroupEventsReader {
  public static getEvents(serverGroup: IServerGroup): PromiseLike<IEventDescription[]> {
    return REST('/applications')
      .path(serverGroup.app, 'serverGroups', serverGroup.account, serverGroup.name, 'events')
      .query({
        region: serverGroup.region,
        provider: serverGroup.cloudProvider,
      })
      .get()
      .catch((error: any): any[] => {
        $log.error(error, 'error retrieving events');
        return [];
      });
  }
}
