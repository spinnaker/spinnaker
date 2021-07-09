import { IVariable, IVariableError } from '../inputs/variableInput.service';
import { IVariableValidator, VariableValidatorService } from './variableValidator.service';

class ListValidator implements IVariableValidator {
  public handles(type: string) {
    return type === 'list';
  }

  public validate(variable: IVariable, errors: IVariableError[]): void {
    ((variable.value as string[]) || []).forEach((listElement, i) => {
      if (!listElement) {
        errors.push({ message: 'Field is required.', key: i });
      }
    });
  }
}

VariableValidatorService.addValidator(new ListValidator());
