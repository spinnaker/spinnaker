import React from 'react';

import { VariableError } from '../VariableError';
import {
  IVariable,
  IVariableInputBuilder,
  IVariableProps,
  IVariableState,
  VariableInputService,
} from './variableInput.service';

class StringInput extends React.Component<IVariableProps, IVariableState> {
  public render() {
    return (
      <div>
        <input
          type="text"
          className="form-control input-sm"
          value={this.props.variable.value || ''}
          onChange={this.extractValue}
          required={true}
        />
        {!this.props.variable.hideErrors && <VariableError errors={this.props.variable.errors} />}
      </div>
    );
  }

  private extractValue = (e: React.ChangeEvent<HTMLInputElement>) => {
    this.props.onChange({ value: e.target.value, type: this.props.variable.type, name: this.props.variable.name });
  };
}

export class StringInputBuilder implements IVariableInputBuilder {
  public handles(type: string): boolean {
    return type === 'string';
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void) {
    return <StringInput variable={variable} onChange={onChange} />;
  }
}

VariableInputService.addInput(new StringInputBuilder());
