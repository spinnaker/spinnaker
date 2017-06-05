import {module} from 'angular';
import {IVariableValidator, VariableValidatorService} from './variableValidator.service';
import {IVariable, IVariableError} from '../inputs/variableInput.service';

class ListValidator implements IVariableValidator {

  public handles(type: string) {
    return type === 'list';
  }

  public validate(variable: IVariable, errors: IVariableError[]): void {
    (variable.value as string[] || []).forEach((listElement, i) => {
      if (!listElement) {
        errors.push({message: 'Field is required.', key: i});
      }
    });
  }
}

export const LIST_VALIDATOR = 'spinnaker.core.pipelineTemplate.listValidator';
module(LIST_VALIDATOR, [])
  .run((variableValidatorService: VariableValidatorService) => variableValidatorService.addValidator(new ListValidator()));
