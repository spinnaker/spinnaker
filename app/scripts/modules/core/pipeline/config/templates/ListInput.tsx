import {module} from 'angular';
import * as React from 'react';
import {Button} from 'react-bootstrap'
import {IVariableInput, VariableInputService, IVariable, IVariableError} from './variableInput.service';
import {VariableError} from './VariableError';
import autoBindMethods from 'class-autobind-decorator';

@autoBindMethods
export class ListInput implements IVariableInput {

  public handles(type: string): boolean {
    return type === 'list';
  }

  public getInput(variable: IVariable, onChange: (values: IVariable) => void): JSX.Element {
    return (
      <div>
        {this.createInputFields(variable, onChange)}
        <Button className="btn btn-block add-new" onClick={this.handleAddValue.bind(this, variable, onChange)}>
          <span className="glyphicon glyphicon-plus-sign"/> Add New
        </Button>
      </div>
    );
  }

  private createInputFields(variable: IVariable, onChange: (variable: IVariable) => void): JSX.Element[] {
    return variable.value.map((v: string, i: number) => {
      return (
        <div className="form-group" key={i} style={{marginBottom: '5px'}}>
          <input
            type="text"
            style={{display: 'inline-block', width: '90%'}}
            className="form-control input-sm"
            value={v}
            onChange={this.createInputChangeHandler(i, variable, onChange)}
            required={true}
          />
          <a onClick={this.handleDeleteValue.bind(this, i, variable, onChange)} className="clickable">
            <span className="glyphicon glyphicon-trash"/>
          </a>
          <VariableError errors={this.findErrorsForInput(variable, i)}/>
        </div>
      );
    });
  }

  private findErrorsForInput(variable: IVariable, inputKey: number): IVariableError[] {
    return variable.errors.filter(e => e.key === inputKey);
  }

  private createInputChangeHandler(i: number,
                                   variable: IVariable,
                                   onChange: (variable: IVariable) => void): (e: React.ChangeEvent<HTMLInputElement>) => void {
    return (e: React.ChangeEvent<HTMLInputElement>) => {
      const list = variable.value.slice();
      list[i] = e.target.value;

      const errors = this.validate(list);
      onChange({value: list, errors, type: variable.type, name: variable.name});
    };
  }

  private handleDeleteValue(i: number, variable: IVariable, onChange: (variable: IVariable) => void): void {
    const list = variable.value.slice();
    list.splice(i, 1);

    const errors = this.validate(list);
    onChange({value: list, errors, type: variable.type, name: variable.name});
  }

  private handleAddValue(variable: IVariable, onChange: (variable: IVariable) => void): void {
    const list = variable.value.slice().concat(['']);

    const errors = this.validate(list);
    onChange({value: list, errors, type: variable.type, name: variable.name});
  }

  private validate(values: string[]): IVariableError[] {
    const errors: IVariableError[] = [];
    values.forEach((v, i) => {
      if (!v) {
        errors.push({message: 'Field is required.', key: i});
      }
    });
    return errors;
  }
}

export const LIST_INPUT = 'spinnaker.core.pipelineTemplate.listInput';
module(LIST_INPUT, [])
  .run((variableInputService: VariableInputService) => variableInputService.addInput(new ListInput()));
