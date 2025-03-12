import React from 'react';

import { HelpField } from '../../../../../help/HelpField';

export interface IStageConfigFieldProps {
  label: string;
  helpKey?: string;
  labelColumns?: number;
  fieldColumns?: number;
  children?: React.ReactNode;
  groupClassName?: string;
}

export const StageConfigField = (props: IStageConfigFieldProps) => (
  <div className={props.groupClassName != null ? props.groupClassName : 'form-group'}>
    <label className={`col-md-${props.labelColumns || 3} sm-label-right`}>
      <span className="label-text">{props.label} </span>
      {props.helpKey && <HelpField id={props.helpKey} />}
    </label>
    <div className={`col-md-${props.fieldColumns || 8}`}>{props.children}</div>
  </div>
);
