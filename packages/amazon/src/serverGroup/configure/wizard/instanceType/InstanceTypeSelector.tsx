import type { FormikProps } from 'formik/dist/types';
import React, { useEffect, useState } from 'react';

import type { IInstanceTypeCategory } from '@spinnaker/core';
import { HelpField } from '@spinnaker/core';

import { AdvancedModeSelector } from './advancedMode/AdvancedModeSelector';
import { AWSProviderSettings } from '../../../../aws.settings';
import type { IAmazonInstanceTypeOverride, IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';
import { SimpleModeSelector } from './simpleMode/SimpleModeSelector';

export interface IInstanceTypeSelectorProps {
  formik: FormikProps<IAmazonServerGroupCommand>;
  instanceTypeDetails: IInstanceTypeCategory[];
}

export function InstanceTypeSelector(props: IInstanceTypeSelectorProps) {
  const { instanceTypeDetails } = props;
  const { values: command, setFieldValue } = props.formik;
  const isLaunchTemplatesEnabled = AWSProviderSettings.serverGroups?.enableLaunchTemplates;

  const useSimpleMode = command.viewState.useSimpleInstanceTypeSelector;
  const [unlimitedCpuCredits, setUnlimitedCpuCredits] = useState(command.unlimitedCpuCredits);

  useEffect(() => {
    if (command.unlimitedCpuCredits !== unlimitedCpuCredits) {
      setFieldValue('unlimitedCpuCredits', unlimitedCpuCredits);
    }
  }, [unlimitedCpuCredits]);

  const handleModeChange = (useSimpleModeNew: boolean) => {
    if (useSimpleMode !== useSimpleModeNew) {
      setFieldValue('viewState', {
        ...command.viewState,
        useSimpleInstanceTypeSelector: useSimpleModeNew,
      });

      // update selected instance type(s) if mode changed.
      // Simple mode uses command.instanceType to track selected type. Advanced mode uses command.launchTemplateOverridesForInstanceType to track selected types.
      const multipleInstanceTypesInProps = command.launchTemplateOverridesForInstanceType;
      const singleInstanceTypeInProps = command.instanceType;

      const toSimple = useSimpleModeNew && multipleInstanceTypesInProps?.length;
      const toAdvanced = !useSimpleModeNew && singleInstanceTypeInProps;
      if (toSimple) {
        const highestPriorityNum = Math.min(...multipleInstanceTypesInProps.map((it) => it.priority));
        const instanceTypeWithHighestPriority = multipleInstanceTypesInProps.find(
          (it) => it.priority === highestPriorityNum,
        ).instanceType;

        setFieldValue('instanceType', instanceTypeWithHighestPriority);
        setFieldValue('launchTemplateOverridesForInstanceType', []);
        command.instanceTypeChanged(command);
      } else if (toAdvanced) {
        const instanceTypes: IAmazonInstanceTypeOverride[] = [
          {
            instanceType: singleInstanceTypeInProps,
            priority: 1,
          },
        ];
        setFieldValue('instanceType', undefined);
        setFieldValue('launchTemplateOverridesForInstanceType', instanceTypes);
        command.launchTemplateOverridesChanged(command);
      }
    }
  };

  const showAdvancedMode = isLaunchTemplatesEnabled && !useSimpleMode;
  if (showAdvancedMode) {
    return (
      <div className="container-fluid form-horizontal" style={{ padding: '0 15px' }}>
        <div>
          <p>
            To configure a single instance type, use
            <a className="clickable" onClick={() => handleModeChange(true)}>
              <span> Simple Mode</span>
            </a>
            .
          </p>
          <i>
            <b>Note:</b> If multiple instance types are already selected in advanced mode, the instance type with
            highest priority will be preserved in simple mode.
          </i>
        </div>
        <AdvancedModeSelector
          formik={props.formik}
          instanceTypeDetails={instanceTypeDetails}
          setUnlimitedCpuCredits={setUnlimitedCpuCredits}
        />
      </div>
    );
  }

  return (
    <div className="container-fluid form-horizontal" style={{ padding: '0 15px' }}>
      <div>
        <span>To configure mixed server groups with multiple instance types,</span>
        {!isLaunchTemplatesEnabled && (
          <span>
            <a
              href={
                'https://spinnaker.io/docs/setup/other_config/server-group-launch-settings/aws-ec2/launch-templates-setup/'
              }
            >
              <span> enable launch templates</span>
            </a>
            <span> and</span>
          </span>
        )}
        <span>
          <a
            className={isLaunchTemplatesEnabled ? 'clickable' : 'disabled'}
            onClick={isLaunchTemplatesEnabled ? () => handleModeChange(false) : () => {}}
          >
            <span> use Advanced Mode </span>
          </a>
          <HelpField id={'aws.serverGroup.advancedMode'} />.
        </span>
        <p></p>
        {isLaunchTemplatesEnabled && (
          <i>
            <b>Note:</b> If an instance type is already selected in simple mode, it will be preserved in advanced mode.
          </i>
        )}
      </div>
      <SimpleModeSelector
        command={command}
        setUnlimitedCpuCredits={setUnlimitedCpuCredits}
        setFieldValue={setFieldValue}
      />
    </div>
  );
}
