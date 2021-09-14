import React, { useEffect, useState } from 'react';
import { ToggleButtonGroup, ToggleSize } from '@spinnaker/core';
import { AwsReactInjector } from '../../../../reactShims';

export interface ICpuCreditsToggleProps {
  unlimitedCpuCredits?: boolean;
  selectedInstanceTypes: string[];
  currentProfile: string;
  setUnlimitedCpuCredits: (unlimitedCpuCredits: boolean | undefined) => void;
}

export function CpuCreditsToggle(props: ICpuCreditsToggleProps) {
  const { selectedInstanceTypes, currentProfile } = props;
  const isBurstingSupportedForAllTypes = AwsReactInjector.awsInstanceTypeService.isBurstingSupportedForAllTypes(
    selectedInstanceTypes,
  );
  const isAtleastOneTypeInProfile = AwsReactInjector.awsInstanceTypeService.getInstanceTypesInCategory(
    selectedInstanceTypes,
    currentProfile,
  ).length
    ? true
    : false;

  const [showToggle, setShowToggle] = useState(false);
  useEffect(() => {
    if (selectedInstanceTypes && selectedInstanceTypes.length) {
      if (!isBurstingSupportedForAllTypes) {
        props.setUnlimitedCpuCredits(undefined);
      }
      setShowToggle(isBurstingSupportedForAllTypes);
    }

    if (currentProfile) {
      setShowToggle(
        selectedInstanceTypes &&
          selectedInstanceTypes.length > 0 &&
          isBurstingSupportedForAllTypes &&
          isAtleastOneTypeInProfile,
      );
    }
  }, [currentProfile, selectedInstanceTypes]);

  return (
    <div className={'row'} style={{ fontSize: '110%' }}>
      {showToggle && (
        <div>
          <ToggleButtonGroup
            toggleSize={ToggleSize.XSMALL}
            propLabel={'Unlimited CPU credits '}
            propHelpFieldId={'aws.serverGroup.unlimitedCpuCredits'}
            tooltipPropOffBtn={'Toggle to turn OFF unlimited CPU credits'}
            displayTextPropOffBtn={'Off'}
            tooltipPropOnBtn={'Toggle to turn ON unlimited CPU credits'}
            displayTextPropOnBtn={'On'}
            onClick={(b) => props.setUnlimitedCpuCredits(b)}
            isPropertyActive={props.unlimitedCpuCredits}
          />
        </div>
      )}
    </div>
  );
}
