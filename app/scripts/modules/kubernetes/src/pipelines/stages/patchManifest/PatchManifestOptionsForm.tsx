import React from 'react';
import Select, { Option } from 'react-select';

import { CheckboxInput, StageConfigField } from '@spinnaker/core';

export interface IPatchManifestOptionsFormProps {
  record: boolean;
  onRecordChange: (record: boolean) => void;
  strategy: string;
  onStrategyChange: (strategy: string) => void;
}

export const PatchManifestOptionsForm: React.FunctionComponent<IPatchManifestOptionsFormProps> = (
  props: IPatchManifestOptionsFormProps,
) => {
  return (
    <div className="form-horizontal">
      <StageConfigField label="Record Patch Annotation" helpKey="kubernetes.manifest.patch.record">
        <CheckboxInput
          checked={props.record}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => props.onRecordChange(e.target.checked)}
        />
      </StageConfigField>
      <StageConfigField label="Merge Strategy" helpKey="kubernetes.manifest.patch.mergeStrategy" fieldColumns={3}>
        <Select
          clearable={false}
          value={props.strategy}
          options={['strategic', 'json', 'merge'].map((k) => ({ value: k, label: k }))}
          onChange={(e: Option<string>) => props.onStrategyChange(e.value)}
        />
      </StageConfigField>
    </div>
  );
};
