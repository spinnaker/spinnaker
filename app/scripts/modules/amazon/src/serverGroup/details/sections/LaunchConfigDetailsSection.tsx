import * as React from 'react';

import { CollapsibleSection, ShowUserData } from '@spinnaker/core';

import { IAmazonServerGroupView } from 'amazon/domain';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export interface ILaunchConfigDetailsSectionState {
  image: any;
}

export class LaunchConfigDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps, ILaunchConfigDetailsSectionState> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);

    this.state = { image: this.getImage(props.serverGroup) };
  }

  private getImage(serverGroup: IAmazonServerGroupView): any {
    const image = serverGroup.image ? serverGroup.image : undefined;
    if (serverGroup.image && serverGroup.image.description) {
      const tags: string[] = serverGroup.image.description.split(', ');
      tags.forEach((tag) => {
        const keyVal = tag.split('=');
        if (keyVal.length === 2 && keyVal[0] === 'ancestor_name') {
          serverGroup.image.baseImage = keyVal[1];
        }
      });
    }
    return image;
  }

  public componentWillReceiveProps(nextProps: IAmazonServerGroupDetailsSectionProps): void {
    this.setState({ image: this.getImage(nextProps.serverGroup) });
  }

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const { image } = this.state;

    if (serverGroup.instanceCounts.total > 0) {
      return (
        <CollapsibleSection heading="Launch Configuration">
          {serverGroup.launchConfig && (
            <dl className="horizontal-when-filters-collapsed">
              <dt>Name</dt>
              <dd>{serverGroup.launchConfig.launchConfigurationName}</dd>

              <dt>Image ID</dt>
              <dd>{serverGroup.launchConfig.imageId}</dd>

              {image.imageLocation && <dt>Image Name</dt>}
              {image.imageLocation && <dd>{image.imageLocation}</dd>}

              {image.baseImage && <dt>Base Image Name</dt>}
              {image.baseImage && <dd>{image.baseImage}</dd>}

              <dt>Instance Type</dt>
              <dd>{serverGroup.launchConfig.instanceType}</dd>

              <dt>IAM Profile</dt>
              <dd>{serverGroup.launchConfig.iamInstanceProfile}</dd>

              <dt>Instance Monitoring</dt>
              <dd>{serverGroup.launchConfig.instanceMonitoring.enabled ? 'enabled' : 'disabled'}</dd>

              {serverGroup.launchConfig.spotPrice && <dt>Spot Price</dt>}
              {serverGroup.launchConfig.spotPrice && <dd>{serverGroup.launchConfig.spotPrice}</dd>}

              {serverGroup.launchConfig.keyName && <dt>Key Name</dt>}
              {serverGroup.launchConfig.keyName && <dd>{serverGroup.launchConfig.keyName}</dd>}

              {serverGroup.launchConfig.kernelId && <dt>Kernel ID</dt>}
              {serverGroup.launchConfig.kernelId && <dd>{serverGroup.launchConfig.kernelId}</dd>}

              {serverGroup.launchConfig.ramdiskId && <dt>Ramdisk ID</dt>}
              {serverGroup.launchConfig.ramdiskId && <dd>{serverGroup.launchConfig.ramdiskId}</dd>}

              <dt>User Data</dt>
              {serverGroup.launchConfig.userData && <dd><ShowUserData serverGroupName={serverGroup.name} userData={serverGroup.launchConfig.userData} /></dd>}
              {!serverGroup.launchConfig.userData && <dd>[none]</dd>}
            </dl>
          )}
        </CollapsibleSection>
      );
    }
    return null;
  }
}
