import React from 'react';

import { NgReact } from '@spinnaker/core';

import { CpuCreditsToggle } from '../CpuCreditsToggle';
import { AWSProviderSettings } from '../../../../../aws.settings';
import type { IAmazonServerGroupCommand } from '../../../serverGroupConfiguration.service';

export interface ISimpleModeSelectorProps {
  command: IAmazonServerGroupCommand;
  setUnlimitedCpuCredits: (unlimitedCpuCredits: boolean | undefined) => void;
  setFieldValue: (field: keyof IAmazonServerGroupCommand, value: any, shouldValidate?: boolean) => void;
}

export function SimpleModeSelector(props: ISimpleModeSelectorProps) {
  const { command } = props;
  const { InstanceArchetypeSelector, InstanceTypeSelector } = NgReact;
  const isLaunchTemplatesEnabled = AWSProviderSettings.serverGroups?.enableLaunchTemplates;
  const isCpuCreditsEnabled = AWSProviderSettings.serverGroups?.enableCpuCredits;

  const instanceProfileChanged = (newProfile: string) => {
    // Instance profile is already set on values.viewState, so just use that value.
    // Once angular is gone from this component tree, we can move all the viewState stuff
    // into react state
    props.setFieldValue('viewState', {
      ...command.viewState,
      instanceProfile: newProfile,
    });
  };

  const instanceTypeChanged = (type: string) => {
    command.instanceTypeChanged(command);
    props.setFieldValue('instanceType', type);
  };

  return (
    <div className="container-fluid form-horizontal">
      <div className="row">
        <InstanceArchetypeSelector
          command={command}
          onTypeChanged={instanceTypeChanged}
          onProfileChanged={instanceProfileChanged}
        />
        <div style={{ padding: '0 15px' }}>
          {command.viewState.instanceProfile && command.viewState.instanceProfile !== 'custom' && (
            <InstanceTypeSelector command={command} onTypeChanged={instanceTypeChanged} />
          )}
        </div>
      </div>
      {isLaunchTemplatesEnabled && isCpuCreditsEnabled && (
        <div className="row">
          <CpuCreditsToggle
            unlimitedCpuCredits={command.unlimitedCpuCredits}
            selectedInstanceTypes={[command.instanceType]}
            currentProfile={command.viewState.instanceProfile}
            setUnlimitedCpuCredits={props.setUnlimitedCpuCredits}
          />
        </div>
      )}
    </div>
  );
}
