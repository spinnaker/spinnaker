import * as React from 'react';

import { IValidationMessageProps, ValidationMessage } from 'core/validation';
import { isUndefined } from 'util';

import { IFieldLayoutProps } from '../interface';

import './StandardFieldLayout.css';

export class StandardFieldLayout extends React.Component<IFieldLayoutProps> {
  public render() {
    const { label, help, input, actions, touched, validationMessage, validationStatus } = this.props;

    const showLabel = !isUndefined(label) || !isUndefined(help);

    const renderMessage = (message: React.ReactNode, type: IValidationMessageProps['type']) =>
      typeof message === 'string' ? <ValidationMessage type={type} message={message} /> : message;

    const isErrorOrWarning = validationStatus === 'error' || validationStatus === 'warning';
    const validation = isErrorOrWarning && !touched ? null : renderMessage(validationMessage, validationStatus);

    return (
      <div className="StandardFieldLayout flex-container-h baseline margin-between-lg">
        {showLabel && (
          <div className="StandardFieldLayout_Label sm-label-right">
            {label} {help}
          </div>
        )}

        <div className="flex-grow">
          <div className="flex-container-v">
            <div className="flex-container-h baseline margin-between-lg StandardFieldLayout_Contents">
              {input} {actions}
            </div>

            <div className="StandardFieldLayout_Validation">{validation}</div>
          </div>
        </div>
      </div>
    );
  }
}
