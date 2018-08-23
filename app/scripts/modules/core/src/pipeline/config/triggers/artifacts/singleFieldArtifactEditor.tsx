import * as React from 'react';
import { isFinite } from 'lodash';
import { HelpField } from 'core';
import { IArtifactEditorProps, IArtifact } from 'core/domain';

export const singleFieldArtifactEditor = (
  fieldKey: keyof IArtifact,
  label: string,
  placeholder: string,
  helpTextKey: string,
) => {
  return (props: IArtifactEditorProps) => {
    const labelColumns = isFinite(props.labelColumns) ? props.labelColumns : 2;
    const fieldColumns = isFinite(props.fieldColumns) ? props.fieldColumns : 8;
    const labelClassName = 'col-md-' + labelColumns;
    const fieldClassName = 'col-md-' + fieldColumns;
    return (
      <div className="col-md-12">
        <div className="form-group row">
          <label className={labelClassName + ' sm-label-right'}>
            {label}
            {helpTextKey && <HelpField id={helpTextKey} />}
          </label>
          <div className={fieldClassName}>
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
          </div>
        </div>
      </div>
    );
  };
};
