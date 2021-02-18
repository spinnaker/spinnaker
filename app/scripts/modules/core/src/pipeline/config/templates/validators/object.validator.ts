import { load } from 'js-yaml';

import { IVariable, IVariableError } from '../inputs/variableInput.service';
import { IVariableValidator, VariableValidatorService } from './variableValidator.service';

class ObjectValidator implements IVariableValidator {
  public handles(type: string) {
    return type === 'object';
  }

  public validate(variable: IVariable, errors: IVariableError[]): void {
    if (!variable.value) {
      errors.push({ message: 'Field is required.' });
    }
    try {
      load(variable.value);
    } catch (e) {
      errors.push({ message: e.message });
    }
  }
}

VariableValidatorService.addValidator(new ObjectValidator());
