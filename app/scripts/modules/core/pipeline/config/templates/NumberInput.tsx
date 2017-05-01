import {module} from 'angular';
import * as React from 'react';
import {IVariableInput, VariableInputService, IVariable, IVariableError} from './variableInput.service';
import autoBindMethods from 'class-autobind-decorator';
import {VariableError} from './VariableError';

@autoBindMethods
export class NumberInput implements IVariableInput {

  public handles(type: string): boolean {
    return ['float', 'int'].includes(type);
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void): JSX.Element {
    return (
      <div>
        <input
          type="number"
          className="form-control input-sm"
          value={variable.value || ''}
          onChange={this.extractValue(variable, onChange)}
          required={true}
        />
        <VariableError errors={variable.errors}/>
      </div>
    );
  }

  private extractValue(variable: IVariable, onChange: (variable: IVariable) => void) {
    return (e: React.ChangeEvent<HTMLInputElement>) => {
      const value: string = e.target.value, // Comes back from input as a string, not a number.
        errors: IVariableError[] = [];

      if (!value) {
        errors.push({message: 'Field is required.'});
      }

      if (variable.type === 'int' && value.split('.').length > 1) {
        errors.push({message: 'Must be an integer.'});
      }

      onChange({value, errors, type: variable.type, name: variable.name});
    }
  }
}

export const NUMBER_INPUT = 'spinnaker.core.pipelineTemplate.numberInput';
module(NUMBER_INPUT, [])
  .run((variableInputService: VariableInputService) => variableInputService.addInput(new NumberInput()));
