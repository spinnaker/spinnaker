import React from 'react';

import { CollapsibleSection, LabeledValue, LabeledValueList } from '@spinnaker/core';
import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export const InstancesDiversificationDetailsSection = ({ serverGroup }: IAmazonServerGroupDetailsSectionProps) => {
  if (!serverGroup.mixedInstancesPolicy) {
    return null;
  }

  const instancesDiversification = serverGroup.mixedInstancesPolicy.instancesDiversification;
  return (
    <CollapsibleSection heading="Instance Diversification">
      <LabeledValueList className="horizontal-when-filters-collapsed">
        <LabeledValue
          label="On-Demand Allocation Strategy"
          helpFieldId="aws.serverGroup.odAllocationStrategy"
          value={instancesDiversification.onDemandAllocationStrategy}
        />
        <LabeledValue
          label="On-Demand Base Capacity"
          helpFieldId="aws.serverGroup.odBase"
          value={instancesDiversification.onDemandBaseCapacity}
        />
        <LabeledValue
          label="On-Demand Percentage Above Base Capacity"
          helpFieldId="aws.serverGroup.odPercentAboveBase"
          value={instancesDiversification.onDemandPercentageAboveBaseCapacity}
        />
        <LabeledValue
          label="Spot Allocation Strategy"
          helpFieldId="aws.serverGroup.spotAllocationStrategy"
          value={instancesDiversification.spotAllocationStrategy}
        />
        {instancesDiversification.spotInstancePools && (
          <LabeledValue
            label="Spot Instance Pools"
            helpFieldId="aws.serverGroup.spotInstancePoolCount"
            value={instancesDiversification.spotInstancePools}
          />
        )}
        <LabeledValue
          label="Max Spot Price"
          helpFieldId="aws.serverGroup.spotMaxPrice"
          value={instancesDiversification.spotMaxPrice || 'on-demand price, default'}
        />
      </LabeledValueList>
    </CollapsibleSection>
  );
};
