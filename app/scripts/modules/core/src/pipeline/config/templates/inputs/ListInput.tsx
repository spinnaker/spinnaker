import React from 'react';
import { Button } from 'react-bootstrap';

import { VariableError } from '../VariableError';
import {
  IVariable,
  IVariableError,
  IVariableInputBuilder,
  IVariableProps,
  IVariableState,
  VariableInputService,
} from './variableInput.service';

class ListInput extends React.Component<IVariableProps, IVariableState> {
  public render() {
    return (
      <div>
        {this.createInputFields()}
        <Button className="btn-block add-new" onClick={this.handleAddValue}>
          <span className="glyphicon glyphicon-plus-sign" /> Add New
        </Button>
      </div>
    );
  }

  private createInputFields(): JSX.Element[] {
    return this.props.variable.value.map((v: string, i: number) => {
      return (
        <div className="form-group" key={i} style={{ marginBottom: '5px' }}>
          <input
            type="text"
            style={{ display: 'inline-block', width: '90%' }}
            className="form-control input-sm"
            value={v}
            onChange={this.extractValue(i)}
            required={true}
          />
          <a onClick={this.handleDeleteValue.bind(this, i)} className="clickable" style={{ marginLeft: '10px' }}>
            <span className="glyphicon glyphicon-trash" />
          </a>
          {!this.props.variable.hideErrors && <VariableError errors={this.findErrorsForInput(i)} />}
        </div>
      );
    });
  }

  private findErrorsForInput(inputKey: number): IVariableError[] {
    return this.props.variable.errors ? this.props.variable.errors.filter((e) => e.key === inputKey) : [];
  }

  private extractValue(i: number) {
    return (e: React.ChangeEvent<HTMLInputElement>) => {
      const list = this.props.variable.value.slice();
      list[i] = e.target.value;
      this.props.onChange({ value: list, type: this.props.variable.type, name: this.props.variable.name });
    };
  }

  private handleDeleteValue(i: number): void {
    const list = this.props.variable.value.slice();
    list.splice(i, 1);
    this.props.onChange({ value: list, type: this.props.variable.type, name: this.props.variable.name });
  }

  private handleAddValue = (): void => {
    const list = this.props.variable.value.slice().concat(['']);
    this.props.onChange({ value: list, type: this.props.variable.type, name: this.props.variable.name });
  };
}

export class ListInputBuilder implements IVariableInputBuilder {
  public handles(type: string): boolean {
    return type === 'list';
  }

  public getInput(variable: IVariable, onChange: (values: IVariable) => void) {
    return <ListInput variable={variable} onChange={onChange} />;
  }
}

VariableInputService.addInput(new ListInputBuilder());
