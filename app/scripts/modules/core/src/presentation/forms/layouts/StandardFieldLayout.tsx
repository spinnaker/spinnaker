import { isUndefined } from 'lodash';
import React from 'react';

import { IFormInputValidation } from '../inputs';
import { ILayoutProps } from './interface';
import { ValidationMessage } from '../validation';

import './StandardFieldLayout.css';

export function StandardFieldLayout(props: ILayoutProps) {
  const { label, help, input, actions, validation = {} as IFormInputValidation } = props;
  const { hidden, messageNode, category } = validation;
  const showLabel = !isUndefined(label) || !isUndefined(help);

  return (
    <div className="StandardFieldLayout flex-container-h baseline margin-between-lg">
      {showLabel && (
        <div className="StandardFieldLayout_Label sm-label-right">
          {label} {help}
        </div>
      )}

      <div className="flex-grow">
        <div className="flex-container-v margin-between-md">
          <div className="flex-container-h baseline space-between margin-between-lg StandardFieldLayout_Contents">
            {input} {actions}
          </div>

          {!hidden && <ValidationMessage message={messageNode} type={category} />}
        </div>
      </div>
    </div>
  );
}
