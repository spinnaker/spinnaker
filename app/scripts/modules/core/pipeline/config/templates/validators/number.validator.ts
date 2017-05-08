import {module} from 'angular';
import {IVariableValidator, VariableValidatorService} from './variableValidator.service';
import {IVariable, IVariableError} from '../inputs/variableInput.service';

class NumberValidator implements IVariableValidator {

  public handles(type: string) {
    return ['int', 'float'].includes(type);
  }

  public validate(variable: IVariable, errors: IVariableError[]): void {
    if (!variable.value) {
      errors.push({message: 'Field is required.'});
    }

    if (variable.type === 'int' && typeof variable.value === 'string' && variable.value.split('.').length > 1) {
      errors.push({message: 'Must be an integer.'});
    }
  }
}

export const NUMBER_VALIDATOR = 'spinnaker.core.pipelineTemplate.numberValidator';
module(NUMBER_VALIDATOR, [])
  .run((variableValidatorService: VariableValidatorService) => variableValidatorService.addValidator(new NumberValidator()));
