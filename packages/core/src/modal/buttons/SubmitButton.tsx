import React from 'react';

import { noop } from '../../utils';
import { Spinner } from '../../widgets/spinners/Spinner';

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
    const type = isFormSubmit ? 'submit' : 'button';

    return (
      <button className={className} disabled={isDisabled} onClick={onClick} type={type}>
        <div className="flex-container-h horizontal middle">
          {(!submitting && <i className="far fa-check-circle" />) || <Spinner mode="circular" />}
          <span className="sp-margin-xs-left">{label || (isNew ? 'Create' : 'Update')}</span>
        </div>
      </button>
    );
  }
}
