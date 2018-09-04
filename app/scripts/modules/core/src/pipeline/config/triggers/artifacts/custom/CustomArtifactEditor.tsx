import * as React from 'react';
import { isFinite } from 'lodash';
import { IArtifact, IArtifactEditorProps } from 'core/domain';

export const CustomArtifactEditor = (props: IArtifactEditorProps) => {
  const labelColumns = isFinite(props.labelColumns) ? props.labelColumns : 2;
  const fieldColumns = isFinite(props.fieldColumns) ? props.fieldColumns : 8;
  const labelClassName = 'col-md-' + labelColumns;
  const fieldClassName = 'col-md-' + fieldColumns;
  const input = (field: keyof IArtifact) => (
    <input
      type="text"
      className="form-control input-sm"
      value={props.artifact[field] || ''}
      onChange={e => props.onChange({ ...props.artifact, [field]: e.target.value })}
    />
  );
  return (
    <div>
      <div className="form-group row">
        <label className={labelClassName + ' sm-label-right'}>Type</label>
        <div className="col-md-3">{input('type')}</div>
        <label className={'col-md-2 sm-label-right'}>Name</label>
        <div className="col-md-3">{input('name')}</div>
      </div>
      <div className="form-group row">
        <label className={labelClassName + ' sm-label-right'}>Version</label>
        <div className="col-md-3">{input('version')}</div>
        <label className={'col-md-2 sm-label-right'}>Location</label>
        <div className="col-md-3">{input('location')}</div>
      </div>
      <div className="form-group row">
        <label className={labelClassName + ' sm-label-right'}>Reference</label>
        <div className={fieldClassName}>{input('reference')}</div>
      </div>
    </div>
  );
};
