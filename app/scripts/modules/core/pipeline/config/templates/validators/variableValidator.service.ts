import {module} from 'angular';
import {IVariable, IVariableError} from '../inputs/variableInput.service';

export interface IVariableValidator {
  handles: (type: string) => boolean;
  validate: (variable: IVariable, errors: IVariableError[]) => void;
}

export class VariableValidatorService {

  private validators = new Set<IVariableValidator>();

  public addValidator(validator: IVariableValidator): void {
    this.validators.add(validator);
  }

  public validate(variable: IVariable): IVariableError[] {
    const errors: IVariableError[] = [];
    this.validators.forEach(v => {
      if (v.handles(variable.type)) {
        v.validate(variable, errors);
      }
    });
    return errors;
  }
}

export let variableValidatorService: VariableValidatorService;
export const VARIABLE_VALIDATOR_SERVICE = 'spinnaker.core.variableValidator.service';
module(VARIABLE_VALIDATOR_SERVICE, [])
  .service('variableValidatorService', VariableValidatorService)
  .run(($injector: any) => variableValidatorService = <VariableValidatorService>$injector.get('variableValidatorService'));
