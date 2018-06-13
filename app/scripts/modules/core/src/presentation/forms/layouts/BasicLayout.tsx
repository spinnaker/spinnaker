import { IValidationMessageProps } from 'core';
import * as React from 'react';
import { IFieldLayoutProps } from '../interface';
import { ValidationMessage } from 'core/validation';

export class BasicLayout extends React.Component<IFieldLayoutProps> {
  public render() {
    const { label, help, input, actions, error, warning, preview } = this.props;

    const renderMessage = (message: string | JSX.Element, type: IValidationMessageProps['type']) =>
      typeof message === 'string' ? <ValidationMessage type={type} message={message} /> : message;

    const validation = renderMessage(error, 'error') || renderMessage(warning, 'warning') || preview;

    return (
      <div className="flex-container-h baseline margin-between-lg">
        <div className="sm-label-right" style={{ minWidth: '120px' }}>
          {label} {help}
        </div>

        <div className="flex-grow">
          <div className="flex-container-v">
            <div className="flex-container-h baseline margin-between-lg">
              {input} {actions}
            </div>

            {validation}
          </div>
        </div>
      </div>
    );
  }
}
