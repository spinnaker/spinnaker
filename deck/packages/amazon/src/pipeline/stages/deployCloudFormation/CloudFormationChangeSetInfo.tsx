import React from 'react';

import type { IStage } from '@spinnaker/core';
import { CheckboxInput, ReactSelectInput, StageConfigField, TextInput } from '@spinnaker/core';

export interface ICloudFormationChangeSetInfoProps {
  stage: IStage;
  updateStageField: (changes: { [key: string]: any }) => void;
}

export const CloudFormationChangeSetInfo = (props: ICloudFormationChangeSetInfoProps) => {
  const { stage, updateStageField } = props;
  const changeSetName = stage.changeSetName || "ChangeSet-${execution['id']}";

  const modifyChangeSetName = (value: string) => {
    updateStageField({ changeSetName: value });
  };

  const toggleExecuteChangeSet = (checked: boolean) => {
    updateStageField({ executeChangeSet: checked });
  };

  const modifyActionOnReplacement = (value: string) => {
    updateStageField({ actionOnReplacement: value });
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
        <CheckboxInput checked={stage.executeChangeSet} onChange={(e) => toggleExecuteChangeSet(e.target.checked)} />
      </StageConfigField>
      {stage.executeChangeSet && (
        <StageConfigField label="If ChangeSet contains a replacement" helpKey="aws.cloudformation.changeSet.options">
          <ReactSelectInput
            clearable={false}
            value={stage.actionOnReplacement}
            options={actionOnReplacementOptions}
            onChange={(e) => modifyActionOnReplacement(e.target.value)}
          />
        </StageConfigField>
      )}
    </div>
  );
};
