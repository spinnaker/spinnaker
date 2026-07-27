import React from 'react';

import { PlatformHealthOverride, Registry, StageConfigField, StageConstants } from '@spinnaker/core';

import { AzureAccountRegionClusterSelector } from '../AzureAccountRegionClusterSelector';

export function AzureDisableAsgExecutionLabel({ stage }: any) {
  const context = stage.masterStage?.context || stage.context || {};

  return (
    <span className="task-label">
      {' '}
      Disable Server Group: {context.serverGroupName} ({context.region}){' '}
    </span>
  );
}

export function AzureDisableAsgStageConfig({ application, pipeline, stage, updateStageField }: any) {
  const updateStage = (changes: any) => {
    Object.assign(stage, changes);
    updateStageField(changes);
  };

  stage.regions = stage.regions || [];
  stage.cloudProvider = 'azure';
  if (
    stage.isNew &&
    application.attributes.platformHealthOnlyShowOverride &&
    stage.interestingHealthProviderNames === undefined
  ) {
    stage.interestingHealthProviderNames = [];
  }
  if (!stage.credentials && application.defaultCredentials.azure) {
    stage.credentials = application.defaultCredentials.azure;
  }
  if (!stage.regions.length && application.defaultRegions.azure) {
    stage.regions.push(application.defaultRegions.azure);
  }
  if (!stage.target) {
    stage.target = StageConstants.TARGET_LIST[0].val;
  }

  return (
    <div className="form-horizontal">
      {!pipeline?.strategy && (
        <AzureAccountRegionClusterSelector application={application} stage={stage} updateStageField={updateStage} />
      )}
      <StageConfigField label="Target">
        <select
          className="form-control input-sm"
          value={stage.target}
          onChange={(e) => updateStage({ target: e.target.value })}
        >
          {StageConstants.TARGET_LIST.map((target: any) => (
            <option key={target.val} value={target.val}>
              {target.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      {application.attributes.platformHealthOnlyShowOverride && (
        <PlatformHealthOverride
          interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
          platformHealthType="azureService"
          onChange={(interestingHealthProviderNames) => updateStage({ interestingHealthProviderNames })}
        />
      )}
    </div>
  );
}

export function registerAzureDisableAsgStage() {
  Registry.pipeline.registerStage({
    key: 'disableServerGroup',
    provides: 'disableServerGroup',
    alias: 'disableAsg',
    cloudProvider: 'azure',
    component: AzureDisableAsgStageConfig,
    executionLabelComponent: AzureDisableAsgExecutionLabel,
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
      },
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'regions' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  } as any);
}
