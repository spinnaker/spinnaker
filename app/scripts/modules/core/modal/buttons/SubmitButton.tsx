import * as React from 'react';
import { Button } from 'react-bootstrap';
import { ReactInjector } from 'core/react';

interface ISubmitButtonProps {
  onClick: () => void;
  isDisabled?: boolean;
  isNew?: boolean;
  submitting: boolean;
  label: string;
}

export class SubmitButton extends React.Component<ISubmitButtonProps, any> {
  public render() {
    const { BusyButtonIndicator } = ReactInjector;
    return (
      <Button
        className="btn btn-primary"
        disabled={this.props.isDisabled}
        onClick={this.props.onClick}
      >
        { !this.props.submitting && (
          <span className="glyphicon glyphicon-ok-circle"/>
        ) || (
          <ButtonBusyIndicator/>
        )}&nbsp;
        {this.props.label || (this.props.isNew ? 'Create' : 'Update')}
      </Button>
    );
  }
}
