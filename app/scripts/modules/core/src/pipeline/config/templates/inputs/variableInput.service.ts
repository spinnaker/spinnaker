import React from 'react';

export interface IVariable {
  name: string;
  value: any;
  type: string;
  errors?: IVariableError[];
  hideErrors?: boolean;
}

export interface IVariableError {
  message: string;
  // In most cases, variables just have error messages.
  // In the case of a list, each item can have its own error, so we have to have a way of tracking which item has an error.
  key?: number;
}

export interface IVariableInputBuilder {
  handles: (type: string) => boolean;
  getInput: (variable: IVariable, onChange: (variable: IVariable) => void) => React.ReactElement<IVariableProps>;
}

export interface IVariableProps {
  variable: IVariable;
  onChange: (variable: IVariable) => void;
}

export interface IVariableState {}

export class VariableInputService {
  private static inputs = new Set<IVariableInputBuilder>();

  public static addInput(input: IVariableInputBuilder): void {
    this.inputs.add(input);
  }

  public static getInputForType(type = 'string'): IVariableInputBuilder {
    return Array.from(this.inputs).find((i) => i.handles(type));
  }
}
