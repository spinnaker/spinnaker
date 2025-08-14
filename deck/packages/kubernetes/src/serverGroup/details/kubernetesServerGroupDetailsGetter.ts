import { isEmpty } from 'lodash';
import { Observable } from 'rxjs';

import type { IManifest, IServerGroupDetailsProps } from '@spinnaker/core';
import { ManifestReader, ServerGroupReader } from '@spinnaker/core';
import type { IKubernetesLoadBalancer, IKubernetesServerGroup, IKubernetesServerGroupView } from '../../interfaces';

function extractServerGroupSummary(props: IServerGroupDetailsProps): PromiseLike<IKubernetesServerGroup> {
  const { app, serverGroup } = props;
  return app.ready().then(() => {
    let summary: IKubernetesServerGroup = app.serverGroups.data.find((toCheck: IKubernetesServerGroup) => {
      return (
        toCheck.name === serverGroup.name &&
        toCheck.account === serverGroup.accountId &&
        toCheck.region === serverGroup.region
      );
    });
    if (!summary) {
      app.loadBalancers.data.some((loadBalancer: IKubernetesLoadBalancer) => {
        if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
          return loadBalancer.serverGroups.some((possibleServerGroup: IKubernetesServerGroup) => {
            if (possibleServerGroup.name === serverGroup.name) {
              summary = possibleServerGroup;
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

export function kubernetesServerGroupDetailsGetter(
  props: IServerGroupDetailsProps,
  autoClose: () => void,
): Observable<IKubernetesServerGroupView> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<IKubernetesServerGroupView>((observer) => {
    extractServerGroupSummary(props).then((summary) => {
      Promise.all([
        ServerGroupReader.getServerGroup(
          app.name,
          serverGroupInfo.accountId,
          serverGroupInfo.region,
          serverGroupInfo.name,
        ),
        ManifestReader.getManifest(serverGroupInfo.accountId, serverGroupInfo.region, serverGroupInfo.name),
      ]).then(([serverGroup, manifest]: [IKubernetesServerGroupView, IManifest]) => {
        // it's possible the summary was not found because the clusters are still loading
        Object.assign(serverGroup, summary, { account: serverGroupInfo.accountId });
        serverGroup.manifest = manifest;
        if (!isEmpty(serverGroup)) {
          observer.next(serverGroup);
        } else {
          autoClose();
        }
      }, autoClose);
    }, autoClose);
  });
}
