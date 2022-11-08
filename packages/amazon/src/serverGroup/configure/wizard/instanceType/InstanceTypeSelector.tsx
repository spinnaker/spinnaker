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
  const { values, setFieldValue } = props.formik;
  const isLaunchTemplatesEnabled = AWSProviderSettings.serverGroups?.enableLaunchTemplates;

  const useSimpleMode = values.viewState.useSimpleInstanceTypeSelector;
  const [unlimitedCpuCredits, setUnlimitedCpuCredits] = useState(values.unlimitedCpuCredits);

  useEffect(() => {
    if (values.unlimitedCpuCredits !== unlimitedCpuCredits) {
      setFieldValue('unlimitedCpuCredits', unlimitedCpuCredits);
    }
  }, [unlimitedCpuCredits]);

  const clearWarnings = () => {
    const { formik } = props;

    // clear for both keys to support consistency between simple and advanced modes
    formik.values.viewState.dirty['instanceType'] = null;
    formik.values.viewState.dirty['launchTemplateOverridesForInstanceType'] = null;

    formik.validateForm();
  };

  const handleModeChange = (useSimpleModeNew: boolean) => {
    if (useSimpleMode !== useSimpleModeNew) {
      setFieldValue('viewState', {
        ...values.viewState,
        useSimpleInstanceTypeSelector: useSimpleModeNew,
      });

      // update selected instance type(s) if mode changed.
      // Simple mode uses command.instanceType to track selected type. Advanced mode uses command.launchTemplateOverridesForInstanceType to track selected types.
      if (useSimpleModeNew) {
        const multipleInstanceTypesInProps = values.launchTemplateOverridesForInstanceType;
        const dirtyMultipleInstanceTypesInProps = values.viewState.dirty.launchTemplateOverridesForInstanceType;

        if (multipleInstanceTypesInProps?.length) {
          const instanceTypeWithHighestPriority = multipleInstanceTypesInProps.reduce((prev, current) => {
            return prev.priority < current.priority ? prev : current;
          }).instanceType;
          setFieldValue('instanceType', instanceTypeWithHighestPriority);
          setFieldValue('launchTemplateOverridesForInstanceType', []);
          values.instanceTypeChanged(values);
        }

        if (dirtyMultipleInstanceTypesInProps?.length) {
          const instanceTypeWithHighestPriorityDirty = dirtyMultipleInstanceTypesInProps.reduce((prev, current) => {
            return prev.priority < current.priority ? prev : current;
          }).instanceType;
          setFieldValue('viewState.dirty.instanceType', instanceTypeWithHighestPriorityDirty);
          setFieldValue('viewState.dirty.launchTemplateOverridesForInstanceType', []);
        }
      } else if (!useSimpleModeNew) {
        const singleInstanceTypeInProps = values.instanceType;
        const dirtySingleInstanceTypeInProps = values.viewState.dirty.instanceType;

        if (singleInstanceTypeInProps) {
          const instanceTypes: IAmazonInstanceTypeOverride[] = [
            {
              instanceType: singleInstanceTypeInProps,
              priority: 1,
            },
          ];
          setFieldValue('instanceType', undefined);
          setFieldValue('launchTemplateOverridesForInstanceType', instanceTypes);
          values.launchTemplateOverridesChanged(values);
        }

        if (dirtySingleInstanceTypeInProps) {
          const dirtyInstanceTypes: IAmazonInstanceTypeOverride[] = [
            {
              instanceType: dirtySingleInstanceTypeInProps,
              priority: 1,
            },
          ];
          setFieldValue('viewState.dirty.instanceType', undefined);
          setFieldValue('viewState.dirty.launchTemplateOverridesForInstanceType', dirtyInstanceTypes);
        }
      }
    }
  };

  const showAdvancedMode = isLaunchTemplatesEnabled && !useSimpleMode;
  if (showAdvancedMode) {
    return (
      <div className="container-fluid form-horizontal" style={{ padding: '0 15px' }}>
        <div>
          <p>
            Switch to
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
          clearWarnings={clearWarnings}
        />
      </div>
    );
  }

  return (
    <div className="container-fluid form-horizontal" style={{ padding: '0 15px' }}>
      <div>
        <span>To configure mixed server groups and/or multiple instance types,</span>
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
        command={values}
        setUnlimitedCpuCredits={setUnlimitedCpuCredits}
        setFieldValue={setFieldValue}
        clearWarnings={clearWarnings}
      />
    </div>
  );
}
