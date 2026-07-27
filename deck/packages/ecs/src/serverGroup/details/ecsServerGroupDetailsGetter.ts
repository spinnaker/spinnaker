import { isEmpty } from 'lodash';
import { Observable } from 'rxjs';

import type { IServerGroup, IServerGroupDetailsProps } from '@spinnaker/core';
import { ServerGroupReader } from '@spinnaker/core';

import { EcsServerGroupTransformer } from '../serverGroup.transformer';

export function ecsServerGroupDetailsGetter(
  props: IServerGroupDetailsProps,
  autoClose: () => void,
): Observable<IServerGroup> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<IServerGroup>((observer) => {
    let cancelled = false;
    let summary: IServerGroup;

    app
      .ready()
      .then(() => {
        if (cancelled) {
          return null;
        }

        summary = app.serverGroups.data.find(
          (candidate: IServerGroup) =>
            candidate.name === serverGroupInfo.name &&
            candidate.account === serverGroupInfo.accountId &&
            candidate.region === serverGroupInfo.region,
        );
        if (!summary) {
          const loadBalancer = app.loadBalancers.data.find(
            (candidate: any) =>
              candidate.account === serverGroupInfo.accountId && candidate.region === serverGroupInfo.region,
          );
          summary = loadBalancer?.serverGroups.find(
            (candidate: IServerGroup) => candidate.name === serverGroupInfo.name,
          );
        }

        return ServerGroupReader.getServerGroup(
          app.name,
          serverGroupInfo.accountId,
          serverGroupInfo.region,
          serverGroupInfo.name,
        );
      })
      .then((serverGroup: IServerGroup) => {
        if (cancelled || serverGroup === null) {
          return;
        }
        if (isEmpty(serverGroup)) {
          autoClose();
          observer.complete();
          return;
        }

        const merged = { ...serverGroup, ...summary, account: serverGroupInfo.accountId } as IServerGroup;
        observer.next(new EcsServerGroupTransformer().normalizeServerGroupDetails(merged as any) as IServerGroup);
        observer.complete();
      })
      .catch(() => {
        if (!cancelled) {
          autoClose();
          observer.complete();
        }
      });

    return () => {
      cancelled = true;
    };
  });
}
