import React from 'react';

import {
  IVariableInputBuilder,
  VariableInputService,
  IVariable,
  IVariableProps,
  IVariableState,
} from './variableInput.service';
import { VariableError } from '../VariableError';

class BooleanInput extends React.Component<IVariableProps, IVariableState> {
  public render() {
    return (
      <div>
        <input type="checkbox" checked={this.props.variable.value || false} onChange={this.extractValue} />
        {!this.props.variable.hideErrors && <VariableError errors={this.props.variable.errors} />}
      </div>
    );
  }

  private extractValue = (): void => {
    this.props.onChange({
      value: !this.props.variable.value,
      type: this.props.variable.type,
      name: this.props.variable.name,
    });
  };
}

export class BooleanInputBuilder implements IVariableInputBuilder {
  public handles(type: string): boolean {
    return type === 'boolean';
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void) {
    return <BooleanInput variable={variable} onChange={onChange} />;
  }
}

VariableInputService.addInput(new BooleanInputBuilder());
