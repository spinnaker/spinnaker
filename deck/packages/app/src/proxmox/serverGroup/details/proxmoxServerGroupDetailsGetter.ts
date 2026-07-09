import { Observable } from 'rxjs-compat';

import type { IServerGroup, IServerGroupDetailsProps } from '@spinnaker/core';
import { ServerGroupReader } from '@spinnaker/core';

function extractServerGroupSummary(props: IServerGroupDetailsProps): PromiseLike<IServerGroup> {
  const { app, serverGroup } = props;
  return app.ready().then(() =>
    app.serverGroups.data.find(
      (sg: IServerGroup) =>
        sg.name === serverGroup.name &&
        (sg as any).account === serverGroup.accountId &&
        sg.region === serverGroup.region,
    ),
  );
}

export function proxmoxServerGroupDetailsGetter(
  props: IServerGroupDetailsProps,
  autoClose: () => void,
): Observable<IServerGroup> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<IServerGroup>((observer) => {
    extractServerGroupSummary(props).then((summary) => {
      ServerGroupReader.getServerGroup(
        app.name,
        serverGroupInfo.accountId,
        serverGroupInfo.region,
        serverGroupInfo.name,
      ).then((serverGroup: IServerGroup) => {
        Object.assign(serverGroup, summary, { account: serverGroupInfo.accountId });
        if (serverGroup.name) {
          observer.next(serverGroup);
        } else {
          autoClose();
        }
      }, autoClose);
    }, autoClose);
  });
}
