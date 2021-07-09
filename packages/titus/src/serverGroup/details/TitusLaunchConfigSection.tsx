import React from 'react';

import { LabeledValue, LabeledValueList, Overridable } from '@spinnaker/core';
import { ITitusServerGroup } from '../../domain';

interface ILaunchConfigSectionProps {
  serverGroup: ITitusServerGroup;
}

@Overridable('titus.serverGroup.launchConfigSection')
export class TitusLaunchConfigSection extends React.Component<ILaunchConfigSectionProps> {
  public render() {
    if (!this.props.serverGroup || !this.props.serverGroup.image) {
      return null;
    }

    const {
      serverGroup: { image, entryPoint, iamProfile, resources },
    } = this.props;

    return (
      <LabeledValueList className="horizontal-when-filters-collapsed">
        {image.dockerImageName && <LabeledValue label="Image Name" value={image.dockerImageName} />}
        {image.dockerImageVersion && <LabeledValue label="Image Version" value={image.dockerImageVersion} />}
        {entryPoint && <LabeledValue label="Entrypoint" value={entryPoint} />}
        {iamProfile && <LabeledValue label="IAM Profile" value={iamProfile} />}
        <LabeledValue label="CPU(s)" value={resources.cpu} />
        <LabeledValue label="Memory" value={`${resources.memory} MB`} />
        <LabeledValue label="Disk" value={`${resources.disk} MB`} />
        <LabeledValue label="Network" value={`${resources.networkMbps} Mbps`} />
        <LabeledValue label="GPU(s)" value={resources.gpu} />
      </LabeledValueList>
    );
  }
}
