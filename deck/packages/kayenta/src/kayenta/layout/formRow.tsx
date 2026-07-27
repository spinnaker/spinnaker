import classNames from 'classnames';
import * as React from 'react';

import { HelpField, ValidationMessage } from '@spinnaker/core';

export interface IFormRowProps {
  label?: string | React.ReactFragment;
  helpId?: string;
  children?: any;
  checkbox?: boolean;
  error?: string;
  warning?: string;
  inputOnly?: boolean;
}

export default function FormRow({ label, helpId, children, checkbox, error, warning, inputOnly }: IFormRowProps) {
  const style: any = {};
  if (!inputOnly) {
    style.marginTop = '5px';
  }
  if (checkbox) {
    style.marginBottom = '0';
  }
  return (
    <div className="form-group row">
      <label className="col-sm-2 control-label sm-label-right">
        {label} {helpId && <HelpField id={helpId} />}
      </label>
      <div className={classNames('col-sm-10', { checkbox })} style={style}>
        {children}
        {error && <ValidationMessage type="error" message={error} />}
        {warning && <ValidationMessage type="warning" message={warning} />}
      </div>
    </div>
  );
}
