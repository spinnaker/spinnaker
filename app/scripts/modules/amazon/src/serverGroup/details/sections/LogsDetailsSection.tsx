import React from 'react';

import { CollapsibleSection, NgReact } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export class LogsDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  public render(): JSX.Element {
    const { ViewScalingActivitiesLink } = NgReact;
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
