import { isEmpty } from 'lodash';
import React from 'react';

import { ILayoutProps } from '../../../presentation';

export function ManualExecutionFieldLayout(props: ILayoutProps) {
  const { label, help, input, actions, validation, required } = props;

  const showLabel = !isEmpty(label) || !isEmpty(help);
  const { hidden, messageNode } = validation;

  return (
    <div className="sp-margin-m-bottom">
      <div className="form-group">
        {showLabel && (
          <label className="col-md-4 sm-label-right break-word">
            {label}
            {required && <span>*</span>} {help}
          </label>
        )}
        <div className="col-md-6">
          <div>
            {input} {actions}
          </div>
          {!hidden && <div className="message">{messageNode}</div>}
        </div>
      </div>
    </div>
  );
}
