import { module } from 'angular';
import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import {
  IVariableInputBuilder, VariableInputService, IVariable, IVariableProps, IVariableState
} from './variableInput.service';
import { VariableError } from '../VariableError';

@BindAll()
class BooleanInput extends React.Component<IVariableProps, IVariableState> {

  public render() {
    return (
      <div>
        <input
          type="checkbox"
          checked={this.props.variable.value || false}
          onChange={this.extractValue}
        />
        {!this.props.variable.hideErrors && <VariableError errors={this.props.variable.errors}/>}
      </div>
    );
  }

  private extractValue(): void {
    this.props.onChange({
      value: !this.props.variable.value,
      type: this.props.variable.type,
      name: this.props.variable.name
    });
  }
}

export class BooleanInputBuilder implements IVariableInputBuilder {

  public handles(type: string): boolean {
    return type === 'boolean';
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void) {
    return <BooleanInput variable={variable} onChange={onChange}/>;
  }
}

export const BOOLEAN_INPUT = 'spinnaker.core.pipelineTemplate.booleanInput';
module(BOOLEAN_INPUT, [])
  .run((variableInputService: VariableInputService) => variableInputService.addInput(new BooleanInputBuilder()));
