import React from 'react';

import { InstanceArchetypeSelector, InstanceTypeSelector } from '@spinnaker/core';

import { CpuCreditsToggle } from '../CpuCreditsToggle';
import { InstanceTypeWarning } from '../InstanceTypeWarning';
import { AWSProviderSettings } from '../../../../../aws.settings';
import type { IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';

export interface ISimpleModeSelectorProps {
  command: IAmazonServerGroupCommand;
  setUnlimitedCpuCredits: (unlimitedCpuCredits: boolean | undefined) => void;
  setFieldValue: (field: keyof IAmazonServerGroupCommand, value: any, shouldValidate?: boolean) => void;
  clearWarnings: () => void;
}

export function SimpleModeSelector(props: ISimpleModeSelectorProps) {
  const { command } = props;
  const [selectedInstanceProfile, setSelectedInstanceProfile] = React.useState(command.viewState.instanceProfile);
  const isLaunchTemplatesEnabled = AWSProviderSettings.serverGroups?.enableLaunchTemplates;
  const isCpuCreditsEnabled = AWSProviderSettings.serverGroups?.enableCpuCredits;

  React.useEffect(() => {
    setSelectedInstanceProfile(command.viewState.instanceProfile);
  }, [command.viewState.instanceProfile]);

  const instanceProfileChanged = (newProfile: string) => {
    setSelectedInstanceProfile(newProfile);
    // Instance profile is already set on values.viewState, so just use that value.
    // Once this component tree is fully standalone, we can move all the viewState stuff
    // into react state
    props.setFieldValue('viewState', {
      ...command.viewState,
      instanceProfile: newProfile,
    });
  };

  const instanceTypeChanged = (type: string) => {
    command.instanceType = type;
    command.instanceTypeChanged(command);
    props.setFieldValue('instanceType', type);
  };

  const commandForSelectedProfile = {
    ...command,
    viewState: {
      ...command.viewState,
      instanceProfile: selectedInstanceProfile,
    },
  };

  return (
    <div className="container-fluid form-horizontal">
      <div className="row">
        <InstanceArchetypeSelector
          command={command}
          onTypeChanged={instanceTypeChanged}
          onProfileChanged={instanceProfileChanged}
        />
        <InstanceTypeWarning dirty={command.viewState.dirty} clearWarnings={props.clearWarnings} />
        <div style={{ padding: '0 15px' }}>
          {selectedInstanceProfile && selectedInstanceProfile !== 'custom' && (
            <InstanceTypeSelector command={commandForSelectedProfile} onTypeChanged={instanceTypeChanged} />
          )}
        </div>
      </div>
      {isLaunchTemplatesEnabled && isCpuCreditsEnabled && (
        <div className="row">
          <CpuCreditsToggle
            unlimitedCpuCredits={command.unlimitedCpuCredits}
            selectedInstanceTypes={[command.instanceType]}
            currentProfile={selectedInstanceProfile}
            setUnlimitedCpuCredits={props.setUnlimitedCpuCredits}
          />
        </div>
      )}
    </div>
  );
}
