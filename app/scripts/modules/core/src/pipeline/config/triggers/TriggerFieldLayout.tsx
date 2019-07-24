import * as React from 'react';

import { IFieldLayoutProps } from 'core/presentation';
import { IValidationMessageProps, ValidationMessage } from 'core/validation';

// This layout is for common Trigger fields such as type and enabled
export function LegacyTriggerCommonLayout(props: IFieldLayoutProps) {
  const { actions, help, input, label, validationMessage, validationStatus } = props;

  const renderMessage = (message: React.ReactNode, type: IValidationMessageProps['type']) =>
    typeof message === 'string' ? <ValidationMessage type={type} message={message} /> : message;

  const isErrorOrWarning = validationStatus === 'error' || validationStatus === 'warning';
  const validation = isErrorOrWarning && renderMessage(validationMessage, validationStatus);

  return (
    <div className="form-group row">
      <div className="col-md-10">
        <div className="form-group">
          <label className="col-md-3 sm-label-right">
            {label} {help}
          </label>
          <div className="col-md-9">{input}</div>
          <div className="col-md-9 col-md-offset-3">{validation}</div>
        </div>
      </div>
      <div className="col-md-2 text-right">{actions}</div>
    </div>
  );
}

// This layout is for trigger-type specific fields such as git repository
// These fields are nested inside a col-md-10 in the parent container
export function LegacyTriggerContentLayout(props: IFieldLayoutProps) {
  const { help, input, label, validationMessage, validationStatus } = props;

  const renderMessage = (message: React.ReactNode, type: IValidationMessageProps['type']) =>
    typeof message === 'string' ? <ValidationMessage type={type} message={message} /> : message;

  const isErrorOrWarning = validationStatus === 'error' || validationStatus === 'warning';
  const validation = isErrorOrWarning && renderMessage(validationMessage, validationStatus);

  return (
    <div className="form-group">
      <label className="col-md-3 sm-label-right">
        {label} {help}
      </label>
      <div className="col-md-9">{input}</div>
      <div className="col-md-9 col-md-offset-3">{validation}</div>
    </div>
  );
}
