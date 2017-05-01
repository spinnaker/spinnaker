import {module} from 'angular';
import * as React from 'react';
import {load} from 'js-yaml';
import {IVariableInput, VariableInputService, IVariable, IVariableError} from './variableInput.service';
import autoBindMethods from 'class-autobind-decorator';
import {VariableError} from './VariableError';

@autoBindMethods
export class ObjectInput implements IVariableInput {

  public handles(type: string): boolean {
    return type === 'object';
  }

  public getInput(variable: IVariable, onChange: (variable: IVariable) => void): JSX.Element {
    return (
      <div>
        <textarea
          className="form-control input-sm"
          rows={5}
          value={variable.value || ''}
          onChange={this.extractValue(variable, onChange)}
          required={true}
        />
        <VariableError errors={variable.errors}/>
      </div>
    );
  }

  private extractValue(variable: IVariable, onChange: (variable: IVariable) => void) {
    return (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      const value: string = e.target.value,
        errors: IVariableError[] = [];

      if (!value) {
        errors.push({message: 'Field is required.'});
      }
      try {
        load(value);
      } catch (e) {
        errors.push({message: e.message});
      }

      onChange({value, errors, type: variable.type, name: variable.name});
    }
  }
}

export const OBJECT_INPUT = 'spinnaker.core.pipelineTemplate.objectInput';
module(OBJECT_INPUT, [])
  .run((variableInputService: VariableInputService) => variableInputService.addInput(new ObjectInput()));
