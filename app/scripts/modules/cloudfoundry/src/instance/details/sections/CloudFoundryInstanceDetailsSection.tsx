import React from 'react';

import { AccountTag, CollapsibleSection, timestamp } from '@spinnaker/core';
import { ICloudFoundryInstance } from '../../../domain';

export interface ICloudFoundryInstanceDetailsSectionProps {
  instance: ICloudFoundryInstance;
}

export class CloudFoundryInstanceDetailsSection extends React.Component<ICloudFoundryInstanceDetailsSectionProps> {
  constructor(props: ICloudFoundryInstanceDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { instance } = this.props;
    return (
      <div>
        <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>Launched</dt>
            <dd>{timestamp(instance.launchTime)}</dd>
            <dt>In</dt>
            <dd>
              <AccountTag account={instance.account} />
            </dd>
            <dt>Region</dt>
            <dd>{instance.region}</dd>
          </dl>
        </CollapsibleSection>
        <CollapsibleSection heading="Status" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>
              <span className={'glyphicon glyphicon-' + instance.healthState + '-triangle'} />
            </dt>
            <dd>{instance.healthState}</dd>

            {instance.details && (
              <div>
                <dt>Details</dt>
                <dd>{instance.details}</dd>
              </div>
            )}
          </dl>
        </CollapsibleSection>
      </div>
    );
  }
}
