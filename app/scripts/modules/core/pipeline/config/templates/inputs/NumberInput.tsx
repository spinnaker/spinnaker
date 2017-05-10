import {module} from 'angular';
import * as React from 'react';
import {
  IVariableInputBuilder, VariableInputService, IVariable,
  IVariableState, IVariableProps
} from './variableInput.service';
import autoBindMethods from 'class-autobind-decorator';
import {VariableError} from '../VariableError';

@autoBindMethods
class NumberInput extends React.Component<IVariableProps, IVariableState> {

  public render() {
    return (
      <div>
        <input
          type="number"
          className="form-control input-sm"
          value={this.props.variable.value || ''}
          onChange={this.extractValue}
          required={true}
        />
        {!this.props.variable.hideErrors && <VariableError errors={this.props.variable.errors}/>}
      </div>
    );
  }

  private extractValue(e: React.ChangeEvent<HTMLInputElement>) {
    const value: string = e.target.value; // Comes back from input as a string, not a number.
    this.props.onChange({value, type: this.props.variable.type, name: this.props.variable.name});
  }
}

export class NumberInputBuilder implements IVariableInputBuilder {

  public handles(type: string): boolean {
    return ['float', 'int'].includes(type);
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void) {
    return <NumberInput variable={variable} onChange={onChange}/>
  }
}

export const NUMBER_INPUT = 'spinnaker.core.pipelineTemplate.numberInput';
module(NUMBER_INPUT, [])
  .run((variableInputService: VariableInputService) => variableInputService.addInput(new NumberInputBuilder()));
