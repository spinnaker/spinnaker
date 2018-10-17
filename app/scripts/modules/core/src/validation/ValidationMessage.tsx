import * as React from 'react';

export interface IValidationMessageProps {
  message: React.ReactNode;
  type: 'error' | 'warning' | 'message';
}

export const ValidationMessage = (props: IValidationMessageProps) => (
  <div className={`${props.type}-message`}>
    <span className="fa fa-exclamation-circle" /> {props.message}
  </div>
);
