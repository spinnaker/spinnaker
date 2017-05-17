import {module} from 'angular';
import {load} from 'js-yaml';
import {IVariableValidator, VariableValidatorService} from './variableValidator.service';
import {IVariable, IVariableError} from '../inputs/variableInput.service';

class ObjectValidator implements IVariableValidator {

  public handles(type: string) {
    return type === 'object';
  }

  public validate(variable: IVariable, errors: IVariableError[]): void {
    if (!variable.value) {
      errors.push({message: 'Field is required.'});
    }
    try {
      load(variable.value);
    } catch (e) {
      errors.push({message: e.message});
    }
  }
}

export const OBJECT_VALIDATOR = 'spinnaker.core.pipelineTemplate.objectValidator';
module(OBJECT_VALIDATOR, [])
  .run((variableValidatorService: VariableValidatorService) => variableValidatorService.addValidator(new ObjectValidator()));
