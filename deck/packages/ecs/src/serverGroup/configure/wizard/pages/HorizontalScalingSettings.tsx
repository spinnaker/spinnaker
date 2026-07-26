import React from 'react';

import { HelpField } from '@spinnaker/core';

import { EcsCapacityProvider } from '../capacityProvider/CapacityProvider';
import type { IEcsWizardPageProps } from './common';

export const HorizontalScalingSettings = ({ command, configureCommand, onFieldChange }: IEcsWizardPageProps) => {
  const useCapacityProviders =
    command.computeOption === 'capacityProviders' ||
    (!command.computeOption && (command.capacityProviderStrategy || []).length > 0);
  const capacity = command.capacity || {};
  const updateCapacity = (field: string, value: number) => onFieldChange('capacity', { ...capacity, [field]: value });

  return (
    <div className="container-fluid form-horizontal" data-test-id="EcsServerGroupWizard.horizontalScaling">
      <div className="form-group">
        <div className="col-md-4 sm-label-right">
          <b>Compute options</b> <HelpField id="ecs.computeOptions" />
        </div>
        <div className="col-md-2 radio">
          <label>
            <input
              checked={!useCapacityProviders}
              data-test-id="ServerGroup.computeOptionsLaunchType"
              id="computeOptionsLaunchType"
              onChange={() => {
                onFieldChange('computeOption', 'launchType');
                onFieldChange('capacityProviderStrategy', []);
              }}
              type="radio"
            />{' '}
            Launch type
          </label>
        </div>
        <div className="col-md-3 radio">
          <label>
            <input
              checked={useCapacityProviders}
              data-test-id="ServerGroup.computeOptionsCapacityProviders"
              id="computeOptionsCapacityProviders"
              onChange={() => {
                onFieldChange('computeOption', 'capacityProviders');
                onFieldChange('launchType', '');
              }}
              type="radio"
            />{' '}
            Capacity Providers
          </label>
        </div>
      </div>

      {useCapacityProviders ? (
        <div className="form-group">
          <EcsCapacityProvider command={command} configureCommand={configureCommand} notifyAngular={onFieldChange} />
        </div>
      ) : (
        <div className="form-group">
          <div className="col-md-5 sm-label-right">
            Launch Type <HelpField id="ecs.launchtype" />
          </div>
          <div className="col-md-3">
            <select
              aria-label="Launch type"
              className="form-control input-sm"
              data-test-id="ServerGroup.launchType"
              onChange={(event) => onFieldChange('launchType', event.target.value)}
              value={command.launchType || ''}
            >
              <option value="">Select...</option>
              {(command.backingData.launchTypes || []).map((launchType) => (
                <option key={launchType} value={launchType}>
                  {launchType}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      <hr />
      {[
        ['desired', 'Desired capacity', 'ecs.capacity.desired'],
        ['min', 'Minimum', 'ecs.capacity.minimum'],
        ['max', 'Maximum', 'ecs.capacity.maximum'],
      ].map(([field, label, help]) => (
        <div className="form-group" key={field}>
          <div className="col-md-5 sm-label-right">
            {label} <HelpField id={help} />
          </div>
          <div className="col-md-2">
            <input
              aria-label={`${label === 'Minimum' || label === 'Maximum' ? `${label} capacity` : label}`}
              className="form-control input-sm no-spel"
              data-test-id={`ServerGroup.capacity.${field}`}
              onChange={(event) => updateCapacity(field, event.target.valueAsNumber)}
              type="number"
              value={capacity[field] ?? ''}
            />
          </div>
        </div>
      ))}

      <div className="form-group">
        <div className="col-md-12 checkbox">
          <label>
            <input
              checked={!!command.useSourceCapacity}
              data-test-id="ServerGroup.useSourceCapacity"
              onChange={(event) => {
                onFieldChange('useSourceCapacity', event.target.checked);
                onFieldChange('preferSourceCapacity', event.target.checked);
              }}
              type="checkbox"
            />{' '}
            <b>If available, use the previous server group's capacity</b>
          </label>{' '}
          <HelpField id="ecs.capacity.overwrite" />
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-12 checkbox">
          <label>
            <input
              checked={!!command.copySourceScalingPoliciesAndActions}
              data-test-id="ServerGroup.copySourceScalingPoliciesAndActions"
              onChange={(event) => onFieldChange('copySourceScalingPoliciesAndActions', event.target.checked)}
              type="checkbox"
            />{' '}
            <b>If available, copy the previous server group's autoscaling policies</b>
          </label>{' '}
          <HelpField id="ecs.capacity.copySourceScalingPoliciesAndActions" />
        </div>
      </div>
    </div>
  );
};
