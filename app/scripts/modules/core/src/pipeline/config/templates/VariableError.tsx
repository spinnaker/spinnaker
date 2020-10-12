import React from 'react';
import { IVariableError } from './inputs/variableInput.service';

export interface IVariableErrorProps {
  errors: IVariableError[];
}

export interface IVariableErrorState {}

export class VariableError extends React.Component<IVariableErrorProps, IVariableErrorState> {
  public render() {
    return (
      <div className="form-group row slide-in">
        {this.props.errors.length > 0 && (
          <div className="error-message">
            <ul style={{ listStyle: 'none' }}>{this.renderErrors()}</ul>
          </div>
        )}
      </div>
    );
  }

  private renderErrors(): JSX.Element[] {
    return this.props.errors.map((e) => <li key={e.key || e.message}>{e.message}</li>);
  }
}
