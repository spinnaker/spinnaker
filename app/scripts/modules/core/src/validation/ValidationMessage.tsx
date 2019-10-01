import * as React from 'react';
import { IValidationCategory } from '../presentation/forms/validation';

export interface IValidationMessageProps {
  message: React.ReactNode;
  type: IValidationCategory | undefined;
  // default: true
  showIcon?: boolean;
}

const iconClassName = {
  success: 'far fa-check-circle',
  error: 'fa fa-exclamation-circle',
  warning: 'fa fa-exclamation-circle',
  message: 'icon-view-1',
  async: 'fa fa-spinner fa-spin',
  none: '',
};

export const ValidationMessage = (props: IValidationMessageProps) => {
  const divClassName = `${props.type}-message`;
  const showIcon = props.showIcon === undefined ? true : props.showIcon;
  const spanClassName = (showIcon && iconClassName[props.type]) || '';

  return (
    <div className={divClassName}>
      <span className={spanClassName} /> {props.message}
    </div>
  );
};
