import React from 'react';
import { EMPTY, from as observableFrom } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  AccountTag,
  CollapsibleSection,
  ConfirmationModalService,
  ServerGroupReader,
  timestamp,
  useDeckRuntimeServices,
} from '@spinnaker/core';

import { OracleImageReader } from '../../image/image.reader';

export function oracleServerGroupDetailsGetter(props: any, autoClose: () => void) {
  const { app, serverGroup } = props;
  return observableFrom(
    ServerGroupReader.getServerGroup(app.name, serverGroup.accountId, serverGroup.region, serverGroup.name).then(
      async (details: any) => {
        details.account = serverGroup.accountId;
        const imageReader = new OracleImageReader();
        const image = await imageReader
          .getImage(details.launchConfig?.imageId, details.region, details.account)
          .catch(() => undefined);
        details.image = image || { id: details.launchConfig?.imageId, name: details.launchConfig?.imageId };
        return details;
      },
    ),
  ).pipe(
    catchError(() => {
      autoClose();
      return EMPTY;
    }),
  );
}

export function OracleServerGroupActions({ app, serverGroup }: any) {
  const { serverGroupWriter } = useDeckRuntimeServices();
  const destroyServerGroup = () => {
    ConfirmationModalService.confirm({
      header: `Really destroy ${serverGroup.name}?`,
      buttonText: `Destroy ${serverGroup.name}`,
      account: serverGroup.account,
      taskMonitorConfig: { application: app, title: `Destroying ${serverGroup.name}` },
      submitMethod: () => serverGroupWriter.destroyServerGroup(serverGroup, app),
    });
  };

  return (
    <div className="actions">
      <button className="btn btn-sm btn-primary" onClick={destroyServerGroup} type="button">
        Destroy Server Group
      </button>
    </div>
  );
}

export function OracleServerGroupInformationSection({ serverGroup }: any) {
  return (
    <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(serverGroup.createdTime)}</dd>
        <dt>In</dt>
        <dd>
          <AccountTag account={serverGroup.account} /> {serverGroup.region}
        </dd>
        <dt>Avail Domain</dt>
        <dd>{serverGroup.zone || serverGroup.launchConfig?.availabilityDomain}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function OracleServerGroupSizeSection({ serverGroup }: any) {
  return (
    <CollapsibleSection heading="Size" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Desired</dt>
        <dd>{serverGroup.capacity?.desired}</dd>
        <dt>Current</dt>
        <dd>{serverGroup.instances?.length || 0}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function OracleServerGroupLaunchConfigSection({ serverGroup }: any) {
  return (
    <CollapsibleSection heading="Launch Configuration">
      <dl className="dl-horizontal dl-narrow">
        <dt>Name</dt>
        <dd>{serverGroup.name}</dd>
        <dt>Image</dt>
        <dd>{serverGroup.image?.name}</dd>
        <dt>Instance Type</dt>
        <dd>{serverGroup.launchConfig?.shape}</dd>
        <dt>VCN</dt>
        <dd>{serverGroup.launchConfig?.vpcId}</dd>
        <dt>Subnet</dt>
        <dd>{serverGroup.launchConfig?.subnetId}</dd>
      </dl>
    </CollapsibleSection>
  );
}
