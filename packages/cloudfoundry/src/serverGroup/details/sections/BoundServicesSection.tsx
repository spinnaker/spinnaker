import { isEmpty } from 'lodash';
import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';
import { ICloudFoundryServiceInstance } from '../../../domain';

export class BoundServicesSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <>
        {!isEmpty(serverGroup.serviceInstances) && (
          <CollapsibleSection heading="Bound Services" defaultExpanded={true}>
            <dl className="dl-horizontal dl-narrow">
              {serverGroup.serviceInstances.map(function (service: ICloudFoundryServiceInstance, index: number) {
                return (
                  <div key={index}>
                    <dt>Name</dt>
                    <dd>{service.name}</dd>
                    <dt>Plan</dt>
                    <dd>{service.plan}</dd>
                    <dt>Tags</dt>
                    {service.tags && <dd>{service.tags.join(', ')}</dd>}
                  </div>
                );
              })}
            </dl>
          </CollapsibleSection>
        )}
      </>
    );
  }
}
