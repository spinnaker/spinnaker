import { module } from 'angular';
import React, { useState } from 'react';
import { react2angular } from 'react2angular';

import {
  CheckboxInput,
  IStage,
  IStageConfigProps,
  ReactSelectInput,
  StageConfigField,
  TextInput,
  withErrorBoundary,
} from '@spinnaker/core';

export interface ICloudFormationChangeSetInfoProps {
  stage: IStage[];
  stageconfig: IStageConfigProps;
}

export const CloudFormationChangeSetInfo = (props: ICloudFormationChangeSetInfoProps) => {
  const { stage, stageconfig } = props;
  const [changeSetName, setChangeSetName] = useState(
    (stage as any).changeSetName ? (stage as any).changeSetName : "ChangeSet-${ execution['id']}",
  );
  const [executeChangeSet, setExecuteChangeSet] = useState((stage as any).executeChangeSet);
  const [actionOnReplacement, setActionOnReplacement] = useState((stage as any).actionOnReplacement);

  const modifyChangeSetName = (value: string) => {
    setChangeSetName(value);
    stageconfig.updateStageField({ changeSetName: value });
  };

  const toggleExecuteChangeSet = (checked: boolean) => {
    setExecuteChangeSet(checked);
    stageconfig.updateStageField({ executeChangeSet: checked });
  };

  const modifyActionOnReplacement = (value: string) => {
    setActionOnReplacement(value);
    stageconfig.updateStageField({ actionOnReplacement: value });
  };

  const actionOnReplacementOptions = [
    { value: 'ask', label: 'ask' },
    { value: 'skip', label: 'skip it' },
    { value: 'execute', label: 'execute it' },
    { value: 'fail', label: 'fail stage' },
  ];

  return (
    <div>
      <hr />
      <h4>ChangeSet Configuration</h4>
      <StageConfigField label="ChangeSet Name">
        <TextInput
          className="form-control"
          type="text"
          value={changeSetName}
          onChange={(e) => modifyChangeSetName(e.target.value)}
        />
      </StageConfigField>
      <StageConfigField label="Execute ChangeSet">
        <CheckboxInput checked={executeChangeSet} onChange={(e) => toggleExecuteChangeSet(e.target.checked)} />
      </StageConfigField>
      {executeChangeSet && (
        <StageConfigField label="If ChangeSet contains a replacement" help-key="aws.cloudformation.changeSet.options">
          <ReactSelectInput
            clearable={false}
            value={actionOnReplacement}
            options={actionOnReplacementOptions}
            onChange={(e) => modifyActionOnReplacement(e.target.value)}
          />
        </StageConfigField>
      )}
    </div>
  );
};

export const CLOUD_FORMATION_CHANGE_SET_INFO = 'spinnaker.amazon.cloudformation.changetset.info.component';

module(CLOUD_FORMATION_CHANGE_SET_INFO, []).component(
  'cloudFormationChangeSetInfo',
  react2angular(withErrorBoundary(CloudFormationChangeSetInfo, 'cloudFormationChangeSetInfo'), [
    'stage',
    'stageconfig',
  ]),
);
