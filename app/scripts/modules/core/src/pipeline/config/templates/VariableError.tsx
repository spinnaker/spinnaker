import * as React from 'react';
import {IVariableError} from './inputs/variableInput.service'

interface IProps {
  errors: IVariableError[]
}

interface IState { }

export class VariableError extends React.Component<IProps, IState> {

  public render() {
    return (
      <div className="form-group row slide-in">
        {this.props.errors.length > 0 && (
          <div className="error-message">
            <ul style={{listStyle: 'none'}}>
              {this.renderErrors()}
            </ul>
          </div>
        )}
      </div>
    );
  }

  private renderErrors(): JSX.Element[] {
    return this.props.errors.map(e => (<li key={e.key || e.message}>{e.message}</li>));
  }
}

