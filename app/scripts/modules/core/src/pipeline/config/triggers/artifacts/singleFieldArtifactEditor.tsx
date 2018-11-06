import * as React from 'react';
import { isFinite } from 'lodash';
import { StageConfigField } from 'core/pipeline/config/stages/core/stageConfigField/StageConfigField';
import { IArtifactEditorProps, IArtifact } from 'core/domain';

export const singleFieldArtifactEditor = (
  fieldKey: keyof IArtifact,
  label: string,
  placeholder: string,
  helpTextKey: string,
): React.SFC<IArtifactEditorProps> => {
  const SingleFieldArtifactEditor = (props: IArtifactEditorProps) => {
    const labelColumns = isFinite(props.labelColumns) ? props.labelColumns : 2;
    const fieldColumns = isFinite(props.fieldColumns) ? props.fieldColumns : 8;
    return (
      <StageConfigField
        label={label}
        helpKey={helpTextKey}
        labelColumns={labelColumns}
        fieldColumns={fieldColumns}
        groupClassName={props.groupClassName}
      >
        <input
          type="text"
          placeholder={placeholder}
          className="form-control input-sm"
          value={props.artifact[fieldKey] || ''}
          onChange={e => {
            const clone = { ...props.artifact };
            clone[fieldKey] = e.target.value;
            props.onChange(clone);
          }}
        />
      </StageConfigField>
    );
  };
  return SingleFieldArtifactEditor;
};
