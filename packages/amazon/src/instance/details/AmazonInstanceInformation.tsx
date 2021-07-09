import React from 'react';

import { CollapsibleSection, LabeledValue, SubnetTag } from '@spinnaker/core';

import { InstanceInformation } from './InstanceInformation';
import { IAmazonInstance } from '../../domain';
import { VpcTag } from '../../vpc/VpcTag';

export interface IAmazonInstanceInformationProps {
  instance: IAmazonInstance;
}

export const AmazonInstanceInformation = ({ instance }: IAmazonInstanceInformationProps) => {
  const { imageId, serverGroup, subnetId, vpcId } = instance;
  return (
    <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <InstanceInformation
          account={instance.account}
          availabilityZone={instance.availabilityZone}
          instanceType={instance.instanceType}
          capacityType={instance.capacityType}
          launchTime={instance.launchTime}
          provider={instance.provider}
          region={instance.region}
          serverGroup={instance.serverGroup}
          showInstanceType={true}
        />
        {serverGroup && <LabeledValue label="VPC" value={<VpcTag vpcId={vpcId} />} />}
        {subnetId && <LabeledValue label="Subnet" value={<SubnetTag subnetId={subnetId} />} />}
        {imageId && <LabeledValue label="Image ID" value={imageId} />}
      </dl>
    </CollapsibleSection>
  );
};
