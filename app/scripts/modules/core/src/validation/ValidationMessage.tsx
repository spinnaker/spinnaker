import * as React from 'react';

export interface IValidationMessageProps {
  message: string | JSX.Element;
  type: 'error' | 'warning';
}

export const ValidationMessage = (props: IValidationMessageProps) => (
  <div className={`${props.type}-message`}>
    <span className="fa fa-exclamation-circle" /> {props.message}
  </div>
);
