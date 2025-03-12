import React from 'react';

import { CollapsibleSection, LabeledValue, LabeledValueList } from '@spinnaker/core';
import type { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export const InstancesDistributionDetailsSection = ({ serverGroup }: IAmazonServerGroupDetailsSectionProps) => {
  if (!serverGroup.mixedInstancesPolicy) {
    return null;
  }

  const instancesDistribution = serverGroup.mixedInstancesPolicy.instancesDistribution;
  return (
    <CollapsibleSection heading="Instances Distribution">
      <LabeledValueList className="horizontal-when-filters-collapsed">
        <LabeledValue
          label="On-Demand Allocation Strategy"
          helpFieldId="aws.serverGroup.odAllocationStrategy"
          value={instancesDistribution.onDemandAllocationStrategy}
        />
        <LabeledValue
          label="On-Demand Base Capacity"
          helpFieldId="aws.serverGroup.odBase"
          value={instancesDistribution.onDemandBaseCapacity}
        />
        <LabeledValue
          label="On-Demand Percentage Above Base Capacity"
          helpFieldId="aws.serverGroup.odPercentAboveBase"
          value={instancesDistribution.onDemandPercentageAboveBaseCapacity}
        />
        <LabeledValue
          label="Spot Allocation Strategy"
          helpFieldId="aws.serverGroup.spotAllocationStrategy"
          value={instancesDistribution.spotAllocationStrategy}
        />
        {instancesDistribution.spotInstancePools && (
          <LabeledValue
            label="Spot Instance Pools"
            helpFieldId="aws.serverGroup.spotInstancePoolCount"
            value={instancesDistribution.spotInstancePools}
          />
        )}
        <LabeledValue
          label="Max Spot Price"
          helpFieldId="aws.serverGroup.spotMaxPrice"
          value={instancesDistribution.spotMaxPrice || 'on-demand price, default'}
        />
      </LabeledValueList>
    </CollapsibleSection>
  );
};
