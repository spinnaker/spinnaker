import React from 'react';

import { CollapsibleSection, HealthCounts } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export class HealthDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  public render(): JSX.Element {
    const { serverGroup } = this.props;

    if (serverGroup.instanceCounts.total > 0) {
      return (
        <CollapsibleSection heading="Health" defaultExpanded={true}>
          <dl className="dl-horizontal dl-narrow">
            <dt>Instances</dt>
            <dd>
              <HealthCounts container={serverGroup.instanceCounts} className="pull-left" />
            </dd>
          </dl>
        </CollapsibleSection>
      );
    }
    return null;
  }
}
