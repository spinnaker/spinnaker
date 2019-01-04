import * as React from 'react';

import { isEmpty } from 'lodash';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryServiceInstance } from 'cloudfoundry/domain';
import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

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
            <dl className="dl-horizontal dl-flex">
              {serverGroup.serviceInstances.map(function(service: ICloudFoundryServiceInstance, index: number) {
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
