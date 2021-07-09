import { IVariable, IVariableError } from '../inputs/variableInput.service';

export interface IVariableValidator {
  handles: (type: string) => boolean;
  validate: (variable: IVariable, errors: IVariableError[]) => void;
}

export class VariableValidatorService {
  private static validators = new Set<IVariableValidator>();

  public static addValidator(validator: IVariableValidator): void {
    this.validators.add(validator);
  }

  public static validate(variable: IVariable): IVariableError[] {
    const errors: IVariableError[] = [];
    this.validators.forEach((v) => {
      if (v.handles(variable.type)) {
        v.validate(variable, errors);
      }
    });
    return errors;
  }
}
