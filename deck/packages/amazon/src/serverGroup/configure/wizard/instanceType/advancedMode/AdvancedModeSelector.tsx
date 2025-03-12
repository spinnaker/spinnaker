import type { FormikProps } from 'formik/dist/types';
import { keyBy } from 'lodash';
import React, { useEffect, useState } from 'react';

import { usePrevious } from '@spinnaker/core';

import { InstanceProfileSelector } from './InstanceProfileSelector';
import { InstanceTypeTable } from './InstanceTypeTable';
import { InstancesDistribution } from './InstancesDistribution';
import type { IAmazonInstanceTypeCategory } from '../../../../../instance/awsInstanceType.service';
import { AwsReactInjector } from '../../../../../reactShims';
import type { IAmazonInstanceTypeOverride, IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';

export interface IAdvancedModeSelectorProps {
  formik: FormikProps<IAmazonServerGroupCommand>;
  instanceTypeDetails: IAmazonInstanceTypeCategory[];
  setUnlimitedCpuCredits: (unlimitedCpuCredits: boolean | undefined) => void;
  clearWarnings: () => void;
}

/**
 * Note: Launch templates support is expected to be enabled if this component is rendered.
 */
export function AdvancedModeSelector(props: IAdvancedModeSelectorProps) {
  const { instanceTypeDetails, setUnlimitedCpuCredits } = props;
  const { values: command, setFieldValue } = props.formik;

  // for the case of MixedInstancesPolicy without overrides, copy command.instanceType to command.launchTemplateOverridesForInstanceType
  const { instanceType, launchTemplateOverridesForInstanceType } = command;
  useEffect(() => {
    if (!launchTemplateOverridesForInstanceType && instanceType) {
      props.formik.setFieldValue('launchTemplateOverridesForInstanceType', [
        { instanceType: command.instanceType, priority: 1 },
      ]);
    }
  });

  const instanceTypesInProps: IAmazonInstanceTypeOverride[] = command.launchTemplateOverridesForInstanceType
    ? command.launchTemplateOverridesForInstanceType
    : undefined;

  const selectedInstanceTypesMap = new Map<string, IAmazonInstanceTypeOverride>(
    Object.entries(keyBy(instanceTypesInProps, 'instanceType')),
  );

  const [instanceProfile, setInstanceProfile] = useState(command.viewState.instanceProfile || 'custom');
  const prevInstanceProfile = usePrevious(instanceProfile);

  const handleProfileChange = (newProfile: string) => {
    setInstanceProfile(newProfile);
    setFieldValue('viewState', {
      ...command.viewState,
      instanceProfile: newProfile,
    });

    // update instance types on profile change
    const hasProfileChanged = prevInstanceProfile && newProfile && prevInstanceProfile !== newProfile;
    const isInstanceTypesUpdateNeeded = newProfile !== 'custom' && instanceTypesInProps && instanceTypesInProps.length;
    if (hasProfileChanged && isInstanceTypesUpdateNeeded) {
      const instanceTypesInProfile: string[] = AwsReactInjector.awsInstanceTypeService.getInstanceTypesInCategory(
        instanceTypesInProps.map((it) => it.instanceType),
        newProfile,
      );
      const newMultipleTypes = instanceTypesInProps.filter((o) => instanceTypesInProfile.includes(o.instanceType));
      setFieldValue('launchTemplateOverridesForInstanceType', newMultipleTypes);
      command.launchTemplateOverridesChanged(command);
    }
  };

  const handleInstanceTypesChange = (types: IAmazonInstanceTypeOverride[]): void => {
    setFieldValue('launchTemplateOverridesForInstanceType', types);
    command.launchTemplateOverridesChanged(command);
  };

  if (!(instanceTypeDetails && instanceTypeDetails.length > 0)) {
    return null;
  }

  return (
    <div className={'advanced-mode-selector'}>
      <InstanceProfileSelector
        currentProfile={instanceProfile}
        handleProfileChange={handleProfileChange}
        instanceProfileList={instanceTypeDetails}
      />
      <InstancesDistribution formik={props.formik} />
      <InstanceTypeTable
        currentProfile={instanceProfile}
        selectedInstanceTypesMap={selectedInstanceTypesMap}
        unlimitedCpuCreditsInCmd={command.unlimitedCpuCredits}
        profileDetails={instanceTypeDetails.find((p) => p.type === instanceProfile)}
        availableInstanceTypesList={
          (command.backingData && command.backingData.filtered && command.backingData.filtered.instanceTypesInfo) || []
        }
        handleInstanceTypesChange={handleInstanceTypesChange}
        setUnlimitedCpuCredits={setUnlimitedCpuCredits}
        viewState={command.viewState}
        clearWarnings={props.clearWarnings}
      />
    </div>
  );
}
