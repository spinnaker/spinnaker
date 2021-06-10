import React from 'react';

import { CollapsibleSection, ViewScalingActivitiesLink } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export class LogsDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  public render(): JSX.Element {
    return (
      <CollapsibleSection heading="Logs">
        <ul>
          <li>
            <ViewScalingActivitiesLink serverGroup={this.props.serverGroup} />
          </li>
        </ul>
      </CollapsibleSection>
    );
  }
}
