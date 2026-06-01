import { isEmpty } from 'lodash';
import { Observable } from 'rxjs';

import type { IServerGroup, IServerGroupDetailsProps } from '@spinnaker/core';
import { ServerGroupReader } from '@spinnaker/core';

export function ecsServerGroupDetailsGetter(
  props: IServerGroupDetailsProps,
  autoClose: () => void,
): Observable<IServerGroup> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<IServerGroup>((observer) => {
    app
      .ready()
      .then(() =>
        ServerGroupReader.getServerGroup(
          app.name,
          serverGroupInfo.accountId,
          serverGroupInfo.region,
          serverGroupInfo.name,
        ),
      )
      .then((serverGroup: IServerGroup) => {
        if (!isEmpty(serverGroup)) {
          observer.next({ ...serverGroup, account: serverGroupInfo.accountId });
        } else {
          autoClose();
        }
      }, autoClose);
  });
}
