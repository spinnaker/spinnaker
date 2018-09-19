import * as React from 'react';

import { NgReact } from 'core/reactShims';
import { noop } from 'core/utils';

export interface ISubmitButtonProps {
  className?: string;
  onClick?: () => void;
  isDisabled?: boolean;
  isFormSubmit?: boolean;
  isNew?: boolean;
  submitting: boolean;
  label: string;
}

export class SubmitButton extends React.Component<ISubmitButtonProps> {
  public static defaultProps = {
    className: 'btn btn-primary',
    onClick: noop,
    isDisabled: false,
    isFormSubmit: false,
    isNew: false,
  };

  public render() {
    const { className, isDisabled, isFormSubmit, isNew, label, onClick, submitting } = this.props;
    const { ButtonBusyIndicator } = NgReact;
    const type = isFormSubmit ? 'submit' : 'button';

    return (
      <button className={className} disabled={isDisabled} onClick={onClick} type={type}>
        {(!submitting && <i className="far fa-check-circle" />) || <ButtonBusyIndicator />}
        &nbsp;
        {label || (isNew ? 'Create' : 'Update')}
      </button>
    );
  }
}
