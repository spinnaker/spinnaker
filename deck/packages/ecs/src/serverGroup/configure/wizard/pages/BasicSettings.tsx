import React from 'react';

import { AccountSelectInput, DeploymentStrategySelector, NameUtils, RegionSelectInput } from '@spinnaker/core';

import type { IEcsWizardPageProps } from './common';

const fieldValue = (value: any) => value?.target?.value ?? value;

export const BasicSettings = ({ application, command, onFieldChange }: IEcsWizardPageProps) => {
  const accounts = command.backingData.accounts || Object.keys(command.backingData.credentialsKeyedByAccount || {});
  const regions = command.backingData.filtered.regions || [];
  const clusters = command.backingData.filtered.ecsClusters || [];

  return (
    <div className="container-fluid form-horizontal" data-test-id="EcsServerGroupWizard.basicSettings">
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Account</div>
        <div className="col-md-7">
          <AccountSelectInput
            aria-label="Account"
            accounts={accounts}
            onChange={(value) => onFieldChange('credentials', fieldValue(value))}
            provider="ecs"
            readOnly={command.viewState.readOnlyFields?.credentials}
            value={command.credentials || ''}
          />
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Region</div>
        <div className="col-md-7">
          <RegionSelectInput
            aria-label="Region"
            account={command.credentials}
            onChange={(value) => onFieldChange('region', fieldValue(value))}
            readOnly={command.viewState.readOnlyFields?.region}
            regions={regions}
            value={command.region || ''}
          />
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-3 sm-label-right">ECS Cluster name</div>
        <div className="col-md-7">
          <select
            aria-label="ECS Cluster name"
            className="form-control input-sm"
            data-test-id="ServerGroup.clusterName"
            onChange={(event) => onFieldChange('ecsClusterName', event.target.value)}
            required
            value={command.ecsClusterName || ''}
          >
            <option value="">Select...</option>
            {clusters.map((cluster) => (
              <option key={cluster} value={cluster}>
                {cluster}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Stack</div>
        <div className="col-md-7">
          <input
            aria-label="Stack"
            className="form-control input-sm no-spel"
            data-test-id="ServerGroup.stack"
            onChange={(event) => onFieldChange('stack', event.target.value)}
            value={command.stack || ''}
          />
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Detail</div>
        <div className="col-md-7">
          <input
            aria-label="Detail"
            className="form-control input-sm no-spel"
            data-test-id="ServerGroup.details"
            onChange={(event) => onFieldChange('freeFormDetails', event.target.value)}
            value={command.freeFormDetails || ''}
          />
        </div>
      </div>
      {!command.viewState.disableStrategySelection && command.selectedProvider && (
        <DeploymentStrategySelector
          command={command as any}
          onFieldChange={onFieldChange}
          onStrategyChange={(_updatedCommand, strategy) => onFieldChange('strategy', strategy.key)}
        />
      )}
      {!command.viewState.hideClusterNamePreview && (
        <div className="well-compact well text-center">
          Your server group will be in the cluster:{' '}
          <strong>{NameUtils.getClusterName(application.name, command.stack, command.freeFormDetails)}</strong>
        </div>
      )}
    </div>
  );
};
