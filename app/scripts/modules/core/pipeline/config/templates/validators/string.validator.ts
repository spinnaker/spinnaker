import {module} from 'angular';
import {IVariableValidator, VariableValidatorService} from './variableValidator.service';
import {IVariable, IVariableError} from '../inputs/variableInput.service';

class StringValidator implements IVariableValidator {

  public handles(type: string) {
    return type === 'string';
  }

  public validate(variable: IVariable, errors: IVariableError[]): void {
    if (!variable.value) {
      errors.push({message: 'Field is required.'});
    }
  }
}

export const STRING_VALIDATOR = 'spinnaker.core.pipelineTemplate.stringValidator';
module(STRING_VALIDATOR, [])
  .run((variableValidatorService: VariableValidatorService) => variableValidatorService.addValidator(new StringValidator()));
