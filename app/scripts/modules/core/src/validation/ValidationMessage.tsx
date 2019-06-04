import * as React from 'react';

export interface IValidationMessageProps {
  message: React.ReactNode;
  type: 'success' | 'error' | 'warning' | 'message' | 'none';
  // default: true
  showIcon?: boolean;
}

const iconClassName = {
  success: 'far fa-check-circle',
  error: 'fa fa-exclamation-circle',
  warning: 'fa fa-exclamation-circle',
  message: 'icon-view-1',
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
