import type { IVariable, IVariableError } from '../inputs/variableInput.service';
import type { IVariableValidator } from './variableValidator.service';
import { VariableValidatorService } from './variableValidator.service';

class StringValidator implements IVariableValidator {
  public handles(type: string) {
    return type === 'string';
  }

  public validate(variable: IVariable, errors: IVariableError[]): void {
    if (!variable.value) {
      errors.push({ message: 'Field is required.' });
    }
  }
}

VariableValidatorService.addValidator(new StringValidator());
