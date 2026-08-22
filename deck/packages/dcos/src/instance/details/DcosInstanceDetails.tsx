import React from 'react';

import { AccountTag, ConfirmationModalService, InstanceWriter, useDataSource } from '@spinnaker/core';

export function DcosInstanceDetails({ app, instance }: any) {
  const dataSource = app.getDataSource('instances');
  const { data: instances, loaded } = useDataSource<any[]>(dataSource);
  const details = instances?.find((toCheck: any) => {
    return (
      (toCheck.id === instance.instanceId || toCheck.name === instance.instanceId) &&
      toCheck.account === instance.account &&
      toCheck.region === instance.region
    );
  });

  const terminate = () => {
    const target = details || instance;
    const instanceId = target.id || target.instanceId || instance.instanceId;
    const submitMethod = () =>
      InstanceWriter.terminateInstance({ ...target, id: instanceId } as any, app).then(() => {
        dataSource.refresh();
      });

    ConfirmationModalService.confirm({
      header: `Really terminate ${instance.instanceId}?`,
      buttonText: 'Terminate',
      account: instance.account,
      taskMonitorConfig: {
        application: app,
        title: `Terminating ${instance.instanceId}`,
      },
      submitMethod,
    });
  };

  if (!loaded) {
    return <div className="horizontal center middle">Loading instance details...</div>;
  }

  return (
    <div className="details-panel">
      <div className="header">
        <div className="header-text horizontal middle">
          <h3>{instance.instanceId}</h3>
        </div>
        <button className="btn btn-sm btn-danger" type="button" onClick={terminate}>
          Terminate
        </button>
      </div>
      <div className="content">
        <dl className="dl-horizontal dl-narrow">
          <dt>Account</dt>
          <dd>
            <AccountTag account={instance.account} />
          </dd>
          <dt>Region</dt>
          <dd>{instance.region}</dd>
          <dt>Status</dt>
          <dd>{details?.health?.state || details?.status || '-'}</dd>
          <dt>JSON</dt>
          <dd>
            <pre>{JSON.stringify(details || instance, null, 2)}</pre>
          </dd>
        </dl>
      </div>
    </div>
  );
}
