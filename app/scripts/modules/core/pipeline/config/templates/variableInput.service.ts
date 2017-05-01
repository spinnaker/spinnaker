import {module} from 'angular';

export interface IVariable {
  name: string;
  value: any;
  type: string;
  errors: IVariableError[];
}

export interface IVariableError {
  message: string;
  // In most cases, variables just have error messages.
  // In the case of a list, each item can have its own error, so we have to have a way of tracking which item has an error.
  key?: number;
}

export interface IVariableInput {
  handles: (type: string) => boolean;
  getInput: (variable: IVariable, onChange: (variable: IVariable) => void) => JSX.Element;
}

export class VariableInputService {

  private inputs = new Set<IVariableInput>();

  public addInput(input: IVariableInput): void {
    this.inputs.add(input);
  }

  public getInputForType(type = 'string'): IVariableInput {
    return Array.from(this.inputs).find(i => i.handles(type));
  }
}

export let variableInputService: VariableInputService;
export const VARIABLE_INPUT_SERVICE = 'spinnaker.core.variableInput.service';
module(VARIABLE_INPUT_SERVICE, [])
  .service('variableInputService', VariableInputService)
  .run(($injector: any) => variableInputService = <VariableInputService>$injector.get('variableInputService'));
