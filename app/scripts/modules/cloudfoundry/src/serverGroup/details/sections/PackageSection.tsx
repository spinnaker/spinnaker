import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import { ICloudFoundryServerGroupDetailsSectionProps } from './ICloudFoundryServerGroupDetailsSectionProps';

export class PackageSection extends React.Component<ICloudFoundryServerGroupDetailsSectionProps> {
  constructor(props: ICloudFoundryServerGroupDetailsSectionProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    return (
      <>
        {serverGroup.droplet && serverGroup.droplet.sourcePackage && (
          <CollapsibleSection heading="Package" defaultExpanded={true}>
            <dl className="dl-horizontal dl-narrow">
              <dt>Checksum</dt>
              <dd>{serverGroup.droplet.sourcePackage.checksum}</dd>
            </dl>
          </CollapsibleSection>
        )}
      </>
    );
  }
}
