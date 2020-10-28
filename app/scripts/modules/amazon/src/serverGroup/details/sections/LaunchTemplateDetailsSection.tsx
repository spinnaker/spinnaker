import React from 'react';

import { CollapsibleSection, LabeledValue, LabeledValueList, ShowUserData } from '@spinnaker/core';
import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { getBaseImageName } from '../utils';

export const LaunchTemplateDetailsSection = ({ serverGroup }: IAmazonServerGroupDetailsSectionProps) => {
  const { image, launchTemplate, name } = serverGroup;
  const baseImage = getBaseImageName(image?.description);

  if (!launchTemplate) {
    return null;
  }

  const { launchTemplateData } = launchTemplate;
  const maxSpotPrice = launchTemplateData?.instanceMarketOptions?.spotOptions?.maxPrice;
  const creditSpecification = launchTemplateData?.creditSpecification?.cpuCredits;

  return (
    <CollapsibleSection heading="Launch Template">
      <LabeledValueList className="horizontal-when-filters-collapsed">
        <LabeledValue label="Name" value={launchTemplate.launchTemplateName} />
        <LabeledValue label="Image ID" value={launchTemplateData.imageId} />
        {image?.imageLocation && <LabeledValue label="Image Name" value={image?.imageLocation} />}
        {baseImage && <LabeledValue label="Base Image Name" value={baseImage} />}
        <LabeledValue label="Instance Type" value={launchTemplateData.instanceType} />
        {creditSpecification && <LabeledValue label="CPU Credit Specification" value={creditSpecification} />}
        <LabeledValue label="IAM Profile" value={launchTemplateData.iamInstanceProfile.name} />
        <LabeledValue
          label="Instance Monitoring"
          value={launchTemplateData.monitoring.enabled ? 'enabled' : 'disabled'}
        />
        {maxSpotPrice && <LabeledValue label="Max Spot Price" value={maxSpotPrice} />}
        {launchTemplateData.keyName && <LabeledValue label="Key Name" value={launchTemplateData.keyName} />}
        {launchTemplateData.kernelId && <LabeledValue label="Kernel ID" value={launchTemplateData.kernelId} />}
        {launchTemplateData.ramDiskId && <LabeledValue label="Ramdisk ID" value={launchTemplateData.ramDiskId} />}
        {launchTemplateData.userData && (
          <LabeledValue
            label="User Data"
            value={<ShowUserData serverGroupName={name} userData={launchTemplateData.userData} />}
          />
        )}
      </LabeledValueList>
    </CollapsibleSection>
  );
};
