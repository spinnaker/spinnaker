import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class MetricsSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const { metricsUri } = serverGroup;
    return (
      <>
        {metricsUri && (
          <CollapsibleSection heading="Metrics" defaultExpanded={true}>
            <div>
              <a href={metricsUri} target="_blank">
                Metrics
              </a>
            </div>
          </CollapsibleSection>
        )}
      </>
    );
  }
}
