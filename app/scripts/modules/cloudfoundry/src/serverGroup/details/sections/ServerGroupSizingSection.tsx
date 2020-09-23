import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class ServerGroupSizingSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <CollapsibleSection heading="Server Group Sizing" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Instances</dt>
          <dd>{serverGroup.instances.length}</dd>
          <dt>Disk (MB)</dt>
          <dd>{serverGroup.diskQuota}</dd>
          <dt>Memory (MB)</dt>
          <dd>{serverGroup.memory}</dd>
        </dl>
      </CollapsibleSection>
    );
  }
}
