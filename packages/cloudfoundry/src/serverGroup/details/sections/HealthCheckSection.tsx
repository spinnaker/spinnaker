import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class HealthCheckSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <CollapsibleSection heading="Health Check" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Type</dt>
          <dd>{serverGroup.healthCheckType === undefined ? 'port' : serverGroup.healthCheckType}</dd>
          {serverGroup.healthCheckType === 'http' && (
            <div>
              <dt>Endpoint</dt>
              <dd>{serverGroup.healthCheckHttpEndpoint}</dd>
            </div>
          )}
        </dl>
      </CollapsibleSection>
    );
  }
}
