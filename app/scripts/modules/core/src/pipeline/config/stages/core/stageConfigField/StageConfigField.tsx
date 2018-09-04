import * as React from 'react';
import { HelpField } from 'core/help/HelpField';

export interface IStageConfigFieldProps {
  label: string;
  helpKey?: string;
  labelColumns?: number;
  fieldColumns?: number;
  children?: React.ReactNode;
}

export const StageConfigField = (props: IStageConfigFieldProps) => (
  <div className="form-group">
    <label className={`col-md-${props.labelColumns || 3} sm-label-right`}>
      <span className="label-text">{props.label} </span>
      {props.helpKey && <HelpField id={props.helpKey} />}
    </label>
    <div className={`col-md-${props.fieldColumns}`}>{props.children}</div>
  </div>
);
