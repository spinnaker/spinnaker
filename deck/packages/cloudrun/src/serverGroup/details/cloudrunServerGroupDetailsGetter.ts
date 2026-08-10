import { isEmpty } from 'lodash';
import { Observable } from 'rxjs';

import type { IServerGroup, IServerGroupDetailsProps } from '@spinnaker/core';
import { ServerGroupReader } from '@spinnaker/core';

export const cloudrunServerGroupDetailsGetter = (
  props: IServerGroupDetailsProps,
  autoClose: () => void,
): Observable<IServerGroup> => {
  const { app, serverGroup } = props;
  return new Observable<IServerGroup>((observer) => {
    ServerGroupReader.getServerGroup(app.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(
      (serverGroupDetails: IServerGroup) => {
        let fromApp = app.serverGroups.data.find((toCheck: IServerGroup) => {
          return (
            toCheck.name === serverGroup.name &&
            toCheck.account === serverGroup.accountId &&
            toCheck.region === serverGroup.region
          );
        });

        if (!fromApp) {
          app.loadBalancers.data.some((loadBalancer: any) => {
            if (loadBalancer.account !== serverGroup.accountId) {
              return false;
            }
            return loadBalancer.serverGroups.some((toCheck: IServerGroup) => {
              if (toCheck.name === serverGroup.name) {
                fromApp = toCheck;
                return true;
              }
              return false;
            });
          });
        }

        const mergedServerGroup = { ...serverGroupDetails, ...fromApp };
        if (isEmpty(mergedServerGroup)) {
          autoClose();
          observer.complete();
          return;
        }

        observer.next(mergedServerGroup);
      },
      () => {
        autoClose();
        observer.complete();
      },
    );
  });
};
