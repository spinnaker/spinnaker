import React from 'react';

import { CollapsibleSection, ShowUserData } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { IAmazonServerGroupView } from '../../../domain';
import { getBaseImageName } from '../utils';

export interface ILaunchConfigDetailsSectionState {
  image: any;
}

export class LaunchConfigDetailsSection extends React.Component<
  IAmazonServerGroupDetailsSectionProps,
  ILaunchConfigDetailsSectionState
> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);

    this.state = { image: this.getImage(props.serverGroup) };
  }

  private getImage(serverGroup: IAmazonServerGroupView): any {
    const image = serverGroup.image ? serverGroup.image : undefined;
    if (serverGroup.image && serverGroup.image.description) {
      image.baseImage = getBaseImageName(serverGroup.image.description);
    }
    return image;
  }

  public componentWillReceiveProps(nextProps: IAmazonServerGroupDetailsSectionProps): void {
    this.setState({ image: this.getImage(nextProps.serverGroup) });
  }

  public render(): JSX.Element {
    const { name, launchConfig } = this.props.serverGroup;
    const { image } = this.state;

    if (launchConfig) {
      return (
        <CollapsibleSection heading="Launch Configuration">
          <dl className="horizontal-when-filters-collapsed">
            <dt>Name</dt>
            <dd>{launchConfig.launchConfigurationName}</dd>

            <dt>Image ID</dt>
            <dd>{launchConfig.imageId}</dd>

            {image && image.imageLocation && <dt>Image Name</dt>}
            {image && image.imageLocation && <dd>{image.imageLocation}</dd>}

            {image && image.baseImage && <dt>Base Image Name</dt>}
            {image && image.baseImage && <dd>{image.baseImage}</dd>}

            <dt>Instance Type</dt>
            <dd>{launchConfig.instanceType}</dd>

            <dt>IAM Profile</dt>
            <dd>{launchConfig.iamInstanceProfile}</dd>

            <dt>Instance Monitoring</dt>
            <dd>{launchConfig.instanceMonitoring.enabled ? 'enabled' : 'disabled'}</dd>

            {launchConfig.spotPrice && <dt>Spot Price</dt>}
            {launchConfig.spotPrice && <dd>{launchConfig.spotPrice}</dd>}

            {launchConfig.keyName && <dt>Key Name</dt>}
            {launchConfig.keyName && <dd>{launchConfig.keyName}</dd>}

            {launchConfig.kernelId && <dt>Kernel ID</dt>}
            {launchConfig.kernelId && <dd>{launchConfig.kernelId}</dd>}

            {launchConfig.ramdiskId && <dt>Ramdisk ID</dt>}
            {launchConfig.ramdiskId && <dd>{launchConfig.ramdiskId}</dd>}

            <dt>User Data</dt>
            {launchConfig.userData && (
              <dd>
                <ShowUserData serverGroupName={name} userData={launchConfig.userData} />
              </dd>
            )}
            {!launchConfig.userData && <dd>[none]</dd>}
          </dl>
        </CollapsibleSection>
      );
    }
    return null;
  }
}
