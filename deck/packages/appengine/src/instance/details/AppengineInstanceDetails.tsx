import React from 'react';

interface IAppengineInstanceDetailsProps {
  instance: {
    account: string;
    instanceId: string;
    provider: string;
    region: string;
  };
}

export function AppengineInstanceDetails({ instance }: IAppengineInstanceDetailsProps) {
  return (
    <div className="details-panel">
      <div className="header">
        <div className="header-text horizontal middle">
          <i className="fa icon-instance" />
          <h3 className="horizontal middle space-between flex-1">{instance.instanceId}</h3>
        </div>
      </div>
      <div className="content">
        <dl className="dl-horizontal dl-narrow">
          <dt>Account</dt>
          <dd>{instance.account}</dd>
          <dt>Region</dt>
          <dd>{instance.region}</dd>
          <dt>Provider</dt>
          <dd>{instance.provider}</dd>
        </dl>
      </div>
    </div>
  );
}
