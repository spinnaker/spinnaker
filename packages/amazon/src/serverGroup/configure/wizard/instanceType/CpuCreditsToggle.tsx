import React from 'react';

import { ToggleButtonGroup, ToggleSize } from '@spinnaker/core';
import { AwsReactInjector } from '../../../../reactShims';

import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface ICpuCreditsToggleProps {
  command: IAmazonServerGroupCommand;
  newInstanceType?: string;
  newProfileType?: string;
  setUnlimitedCpuCredits: (unlimitedCpuCredits: boolean | undefined) => void;
}

export function CpuCreditsToggle(props: ICpuCreditsToggleProps) {
  const [showToggle, setShowToggle] = React.useState(false);
  React.useEffect(() => {
    if (props.newInstanceType) {
      const isBurstingSupportedForNewInstance = AwsReactInjector.awsInstanceTypeService.isBurstingSupported(
        props.newInstanceType,
      );
      if (!isBurstingSupportedForNewInstance) {
        props.setUnlimitedCpuCredits(undefined);
      }
      setShowToggle(isBurstingSupportedForNewInstance);
    }

    if (props.newProfileType) {
      const { instanceType } = props.command;
      const isTypeInProfile = AwsReactInjector.awsInstanceTypeService.isInstanceTypeInCategory(
        instanceType,
        props.newProfileType,
      );
      const isBurstingSupportedForInstance = AwsReactInjector.awsInstanceTypeService.isBurstingSupported(instanceType);
      setShowToggle(instanceType && isTypeInProfile && isBurstingSupportedForInstance);
    }
  }, [props.newProfileType, props.newInstanceType]);

  const handleToggleChange = (state: boolean) => {
    props.setUnlimitedCpuCredits(state);
  };

  return (
    <div>
      {showToggle && (
        <div className="row">
          <ToggleButtonGroup
            toggleSize={ToggleSize.XSMALL}
            propLabel={'Unlimited CPU credits '}
            propHelpFieldId={'aws.serverGroup.unlimitedCpuCredits'}
            tooltipPropOffBtn={'Toggle to turn OFF unlimited CPU credits'}
            displayTextPropOffBtn={'Off'}
            tooltipPropOnBtn={'Toggle to turn ON unlimited CPU credits'}
            displayTextPropOnBtn={'On'}
            onClick={handleToggleChange}
            isPropertyActive={props.command.unlimitedCpuCredits}
          />
        </div>
      )}
    </div>
  );
}
