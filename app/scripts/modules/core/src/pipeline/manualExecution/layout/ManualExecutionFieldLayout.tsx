import * as React from 'react';
import { isEmpty } from 'lodash';

import { IValidationMessageProps, ValidationMessage } from 'core/validation';
import { IFieldLayoutProps } from 'core/presentation';

export class ManualExecutionFieldLayout extends React.Component<IFieldLayoutProps> {
  public render() {
    const { label, help, input, actions, touched, validationMessage, validationStatus } = this.props;

    const showLabel = !isEmpty(label) || !isEmpty(help);

    const renderMessage = (message: React.ReactNode, type: IValidationMessageProps['type']) =>
      typeof message === 'string' ? <ValidationMessage type={type} message={message} /> : message;

    const isErrorOrWarning = validationStatus === 'error' || validationStatus === 'warning';
    const validation = isErrorOrWarning && !touched ? null : renderMessage(validationMessage, validationStatus);

    return (
      <div className="sp-margin-m-bottom">
        <div className={'form-group'}>
          {showLabel && (
            <label className={'col-md-4 sm-label-right'}>
              {label} {help}
            </label>
          )}
          <div className="col-md-6">
            <div>
              {input} {actions}
            </div>
            <div className="message">{validation}</div>
          </div>
        </div>
      </div>
    );
  }
}
