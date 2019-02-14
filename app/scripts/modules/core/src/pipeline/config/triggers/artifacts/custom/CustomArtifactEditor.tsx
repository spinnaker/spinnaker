import * as React from 'react';
import { isFinite } from 'lodash';
import { IArtifact, IArtifactEditorProps } from 'core/domain';

const input = (artifact: IArtifact, field: keyof IArtifact, onChange: (a: IArtifact) => void) => (
  <input
    type="text"
    className="form-control input-sm"
    value={artifact[field] || ''}
    onChange={e => onChange({ ...artifact, [field]: e.target.value })}
  />
);

const SingleColumnCustomArtifactEditor = (props: IArtifactEditorProps) => {
  const { artifact, onChange, groupClassName } = props;
  const labelColumns = isFinite(props.labelColumns) ? props.labelColumns : 2;
  const fieldColumns = isFinite(props.fieldColumns) ? props.fieldColumns : 8;
  const labelClassName = 'col-md-' + labelColumns;
  const fieldClassName = 'col-md-' + fieldColumns;
  const formGroupClasses = groupClassName != null ? groupClassName : 'form-group row';
  return (
    <div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Type</label>
        <div className={fieldClassName}>{input(artifact, 'type', onChange)}</div>
      </div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Name</label>
        <div className={fieldClassName}>{input(artifact, 'name', onChange)}</div>
      </div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Version</label>
        <div className={fieldClassName}>{input(artifact, 'version', onChange)}</div>
      </div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Location</label>
        <div className={fieldClassName}>{input(artifact, 'location', onChange)}</div>
      </div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Reference</label>
        <div className={fieldClassName}>{input(artifact, 'reference', onChange)}</div>
      </div>
    </div>
  );
};

const MultiColumnCustomArtifactEditor = (props: IArtifactEditorProps) => {
  const { artifact, onChange, groupClassName } = props;
  const labelColumns = isFinite(props.labelColumns) ? props.labelColumns : 2;
  const fieldColumns = isFinite(props.fieldColumns) ? props.fieldColumns : 8;
  const labelClassName = 'col-md-' + labelColumns;
  const fieldClassName = 'col-md-' + fieldColumns;
  const formGroupClasses = groupClassName != null ? groupClassName : 'form-group row';
  return (
    <div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Type</label>
        <div className="col-md-3">{input(artifact, 'type', onChange)}</div>
        <label className={'col-md-2 sm-label-right'}>Name</label>
        <div className="col-md-3">{input(artifact, 'name', onChange)}</div>
      </div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Version</label>
        <div className="col-md-3">{input(artifact, 'version', onChange)}</div>
        <label className={'col-md-2 sm-label-right'}>Location</label>
        <div className="col-md-3">{input(artifact, 'location', onChange)}</div>
      </div>
      <div className={formGroupClasses}>
        <label className={labelClassName + ' sm-label-right'}>Reference</label>
        <div className={fieldClassName}>{input(artifact, 'reference', onChange)}</div>
      </div>
    </div>
  );
};

export const CustomArtifactEditor = (props: IArtifactEditorProps) => {
  if (props.singleColumn) {
    return SingleColumnCustomArtifactEditor(props);
  } else {
    return MultiColumnCustomArtifactEditor(props);
  }
};
