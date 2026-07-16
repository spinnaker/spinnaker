import React, { useEffect } from 'react';

import type { IStage, IStageConfigProps } from '@spinnaker/core';
import { StageConfigField, StageConstants } from '@spinnaker/core';

type AmazonStageFieldType = 'account' | 'regions' | 'selectionStrategy' | 'target' | 'text';

export interface IAmazonStageField {
  fieldName: string;
  label: string;
  type?: AmazonStageFieldType;
}

const selectionStrategies = [
  {
    label: 'Largest',
    val: 'LARGEST',
    description: 'When multiple server groups exist, prefer the server group with the most instances',
  },
  { label: 'Newest', val: 'NEWEST', description: 'When multiple server groups exist, prefer the newest' },
  { label: 'Oldest', val: 'OLDEST', description: 'When multiple server groups exist, prefer the oldest' },
  { label: 'Fail', val: 'FAIL', description: 'When multiple server groups exist, fail' },
];

const findImageFields: IAmazonStageField[] = [
  { fieldName: 'credentials', label: 'Account', type: 'account' },
  { fieldName: 'regions', label: 'Region', type: 'regions' },
  { fieldName: 'cluster', label: 'Cluster' },
  { fieldName: 'selectionStrategy', label: 'Server Group Selection', type: 'selectionStrategy' },
];

const amazonStageFields: { [type: string]: IAmazonStageField[] } = {
  bake: [
    { fieldName: 'package', label: 'Package' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
  ],
  cloneServerGroup: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'region', label: 'Region' },
    { fieldName: 'targetCluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
  ],
  destroyAsg: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
  ],
  destroyServerGroup: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
  ],
  disableAsg: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
  ],
  disableCluster: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'remainingEnabledServerGroups', label: 'Keep enabled Server Groups' },
  ],
  disableServerGroup: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
  ],
  enableAsg: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
  ],
  enableServerGroup: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
  ],
  findAmi: findImageFields,
  findImage: findImageFields,
  findImageFromTags: [
    { fieldName: 'packageName', label: 'Package' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'tags', label: 'Tags' },
  ],
  modifyAwsScalingProcess: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
    { fieldName: 'action', label: 'Action' },
    { fieldName: 'processes', label: 'Processes' },
  ],
  modifyScalingProcess: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
    { fieldName: 'action', label: 'Action' },
    { fieldName: 'processes', label: 'Processes' },
  ],
  resizeAsg: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
    { fieldName: 'action', label: 'Action' },
  ],
  resizeServerGroup: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'target', label: 'Server Group', type: 'target' },
    { fieldName: 'action', label: 'Action' },
  ],
  rollbackCluster: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
  ],
  scaleDownCluster: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'remainingFullSizeServerGroups', label: 'Keep full size Server Groups' },
  ],
  shrinkCluster: [
    { fieldName: 'credentials', label: 'Account', type: 'account' },
    { fieldName: 'regions', label: 'Region', type: 'regions' },
    { fieldName: 'cluster', label: 'Cluster' },
    { fieldName: 'shrinkToSize', label: 'Shrink to Server Groups' },
  ],
};

const defaultFields: IAmazonStageField[] = [
  { fieldName: 'credentials', label: 'Account', type: 'account' },
  { fieldName: 'regions', label: 'Region', type: 'regions' },
];

export function getAmazonStageFields(stage: Partial<IStage>): IAmazonStageField[] {
  return amazonStageFields[stage.type || stage.alias || stage.key || ''] || defaultFields;
}

function getFieldValue(stage: IStage, field: IAmazonStageField): string {
  if (field.type === 'regions') {
    return (stage.regions || []).join(', ');
  }
  if (field.type === 'account') {
    return stage[field.fieldName] || stage.account || '';
  }
  return stage[field.fieldName] || '';
}

function renderField(stage: IStage, field: IAmazonStageField, updateStageField: IStageConfigProps['updateStageField']) {
  if (field.type === 'selectionStrategy') {
    return (
      <select
        className="form-control input-sm"
        onChange={(event) => updateStageField({ [field.fieldName]: event.target.value })}
        value={stage[field.fieldName] || selectionStrategies[0].val}
      >
        {selectionStrategies.map((strategy) => (
          <option key={strategy.val} title={strategy.description} value={strategy.val}>
            {strategy.label}
          </option>
        ))}
      </select>
    );
  }

  if (field.type === 'target') {
    return (
      <select
        className="form-control input-sm"
        onChange={(event) => updateStageField({ [field.fieldName]: event.target.value })}
        value={stage[field.fieldName] || ''}
      >
        <option value="">Select server group</option>
        {StageConstants.TARGET_LIST.map((target) => (
          <option key={target.val} title={target.description} value={target.val}>
            {target.label}
          </option>
        ))}
      </select>
    );
  }

  const updateField = (value: string) => {
    if (field.type === 'regions') {
      updateStageField({
        regions: value
          .split(',')
          .map((region) => region.trim())
          .filter(Boolean),
      });
      return;
    }
    if (field.type === 'account') {
      updateStageField({ [field.fieldName]: value, account: value });
      return;
    }
    updateStageField({ [field.fieldName]: value });
  };

  return (
    <input
      className="form-control input-sm"
      onChange={(event) => updateField(event.target.value)}
      value={getFieldValue(stage, field)}
    />
  );
}

export function AmazonStageConfig({ application, stage, updateStageField }: IStageConfigProps) {
  const fields = getAmazonStageFields(stage);

  useEffect(() => {
    const defaults: any = {};
    if (!stage.cloudProvider) {
      defaults.cloudProvider = 'aws';
    }
    if (fields.some((field) => field.type === 'regions') && !stage.regions) {
      defaults.regions = [];
    }
    if (fields.some((field) => field.type === 'selectionStrategy') && !stage.selectionStrategy) {
      defaults.selectionStrategy = selectionStrategies[0].val;
    }
    if (fields.some((field) => field.type === 'selectionStrategy') && stage.onlyEnabled === undefined) {
      defaults.onlyEnabled = true;
    }
    if (fields.some((field) => field.type === 'account') && !stage.credentials && application.defaultCredentials.aws) {
      defaults.credentials = application.defaultCredentials.aws;
      defaults.account = application.defaultCredentials.aws;
    }
    if (
      fields.some((field) => field.type === 'regions') &&
      (!stage.regions || !stage.regions.length) &&
      application.defaultRegions.aws
    ) {
      defaults.regions = [application.defaultRegions.aws];
    }
    if (Object.keys(defaults).length) {
      updateStageField(defaults);
    }
  }, [
    application,
    fields,
    stage.cloudProvider,
    stage.credentials,
    stage.onlyEnabled,
    stage.regions,
    stage.selectionStrategy,
    updateStageField,
  ]);

  return (
    <div className="form-horizontal">
      {fields.map((field) => (
        <StageConfigField key={field.fieldName} label={field.label}>
          {renderField(stage, field, updateStageField)}
        </StageConfigField>
      ))}
    </div>
  );
}
