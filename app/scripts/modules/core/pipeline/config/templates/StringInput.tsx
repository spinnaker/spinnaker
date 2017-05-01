import {module} from 'angular';
import * as React from 'react';
import {IVariableInput, VariableInputService, IVariable, IVariableError} from './variableInput.service';
import autoBindMethods from 'class-autobind-decorator';
import {VariableError} from './VariableError';

@autoBindMethods
export class StringInput implements IVariableInput {

  public handles(type: string): boolean {
    return type === 'string';
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void): JSX.Element {
    return (
      <div>
        <input
          type="text"
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
      const value: string = e.target.value,
        errors: IVariableError[] = [];

      if (!value) {
        errors.push({message: 'Field is required.'});
      }

      onChange({value, errors, type: variable.type, name: variable.name});
    }
  }
}

export const STRING_INPUT = 'spinnaker.core.pipelineTemplate.stringInput';
module(STRING_INPUT, [])
  .run((variableInputService: VariableInputService) => variableInputService.addInput(new StringInput()));
