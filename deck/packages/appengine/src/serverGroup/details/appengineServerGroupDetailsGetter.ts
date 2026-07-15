import { isEmpty } from 'lodash';
import { Observable } from 'rxjs';

import type { IServerGroupDetailsProps } from '@spinnaker/core';
import { ServerGroupReader } from '@spinnaker/core';

import type { IAppengineLoadBalancer, IAppengineServerGroup } from '../../domain';

function extractServerGroupSummary(props: IServerGroupDetailsProps): PromiseLike<IAppengineServerGroup> {
  const { app, serverGroup } = props;
  return app.ready().then(() => {
    let summary: IAppengineServerGroup = app.serverGroups.data.find((candidate: IAppengineServerGroup) => {
      return (
        candidate.name === serverGroup.name &&
        candidate.account === serverGroup.accountId &&
        candidate.region === serverGroup.region
      );
    });

    if (!summary) {
      app.loadBalancers.data.some((loadBalancer: IAppengineLoadBalancer) => {
        if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
          return (loadBalancer.serverGroups || []).some((candidate: IAppengineServerGroup) => {
            if (candidate.name === serverGroup.name) {
              summary = candidate;
              return true;
            }
            return false;
          });
        }
        return false;
      });
    }

    return summary;
  });
}

export function appengineServerGroupDetailsGetter(
  props: IServerGroupDetailsProps,
  autoClose: () => void,
): Observable<IAppengineServerGroup> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<IAppengineServerGroup>((observer) => {
    extractServerGroupSummary(props).then((summary) => {
      ServerGroupReader.getServerGroup(
        app.name,
        serverGroupInfo.accountId,
        serverGroupInfo.region,
        serverGroupInfo.name,
      ).then((serverGroup: IAppengineServerGroup) => {
        Object.assign(serverGroup, summary, { account: serverGroupInfo.accountId });

        if (!isEmpty(serverGroup)) {
          observer.next(serverGroup);
        } else {
          autoClose();
        }
      }, autoClose);
    }, autoClose);
  });
}
