import React from 'react';

import { VariableError } from '../VariableError';
import {
  IVariable,
  IVariableInputBuilder,
  IVariableProps,
  IVariableState,
  VariableInputService,
} from './variableInput.service';

class ObjectInput extends React.Component<IVariableProps, IVariableState> {
  public render() {
    return (
      <div>
        <textarea
          className="form-control input-sm"
          rows={5}
          value={this.props.variable.value || ''}
          onChange={this.extractValue}
          required={true}
        />
        {!this.props.variable.hideErrors && <VariableError errors={this.props.variable.errors} />}
      </div>
    );
  }

  private extractValue = (e: React.ChangeEvent<HTMLTextAreaElement>): void => {
    this.props.onChange({ value: e.target.value, type: this.props.variable.type, name: this.props.variable.name });
  };
}

export class ObjectInputBuilder implements IVariableInputBuilder {
  public handles(type: string): boolean {
    return type === 'object';
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void) {
    return <ObjectInput variable={variable} onChange={onChange} />;
  }
}

VariableInputService.addInput(new ObjectInputBuilder());
