import React from 'react';

import { HelpTextExpandedContext } from 'core/help';
import { IFormInputValidation } from '../inputs';
import { ValidationMessage } from '../validation';

import { ILayoutProps } from './interface';

import '../forms.less';

export function ResponsiveFieldLayout(props: ILayoutProps) {
  const { label, help, input, actions, validation = {} as IFormInputValidation } = props;
  const { hidden, messageNode, category } = validation;
  const showLabel = !!label || !!help;

  const helpUnder = false;

  return (
    <HelpTextExpandedContext.Provider value={helpUnder}>
      <div className="sp-formItem">
        <div className="sp-formItem__left">
          {showLabel && (
            <div className="sp-formLabel">
              {label} {!helpUnder && help}
            </div>
          )}
        </div>
        <div className="sp-formItem__right">
          <div className="sp-form">
            <span className="field">
              {input} {actions}
            </span>
          </div>
          {helpUnder && help && <div className="description">{help}</div>}
          {!hidden && <ValidationMessage message={messageNode} type={category} />}
        </div>
      </div>
    </HelpTextExpandedContext.Provider>
  );
}
