import * as React from 'react';
import { Button } from 'react-bootstrap';

import { NgReact } from 'core/reactShims';
import { noop } from 'core/utils';

export interface ISubmitButtonProps {
  onClick?: () => void;
  isDisabled?: boolean;
  isFormSubmit?: boolean;
  isNew?: boolean;
  submitting: boolean;
  label: string;
}

export class SubmitButton extends React.Component<ISubmitButtonProps> {
  public render() {
    const { isDisabled, isFormSubmit, isNew, label, onClick, submitting } = this.props;
    const { ButtonBusyIndicator } = NgReact;
    return (
      <Button
        className="btn btn-primary"
        disabled={isDisabled}
        onClick={onClick ? onClick : noop}
        type={isFormSubmit ? 'submit' : 'button'}
      >
        {(!submitting && <i className="far fa-check-circle" />) || <ButtonBusyIndicator />}&nbsp;
        {label || (isNew ? 'Create' : 'Update')}
      </Button>
    );
  }
}
