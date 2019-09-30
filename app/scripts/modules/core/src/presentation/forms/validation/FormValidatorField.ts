import { IFormValidatorField, IValidator } from './validation';

/**
 * Encapsulates a form field and the validation rules defined for that field.
 *
 * By default a ValidatableField is optional.
 */
export class FormValidatorField implements IFormValidatorField {
  constructor(public name: string, public label: string) {}

  public isSpelAware: boolean;
  public isRequired: boolean;
  public isRequiredMessage: string;
  public validators: IValidator[] = [];

  /** Causes the field to fail validation if the value is undefined, null, or empty string. */
  public required(message?: string): FormValidatorField {
    this.isRequired = true;
    this.isRequiredMessage = message;
    return this;
  }

  /**
   * Causes the field to pass validation if the value is undefined, null, or empty string.
   * Fields are default by default.
   */
  public optional(): FormValidatorField {
    this.isRequired = false;
    this.isRequiredMessage = undefined;
    return this;
  }

  /** Causes the field to pass validation if the value contains SpEL */
  public spelAware(isSpelAware = true): FormValidatorField {
    this.isSpelAware = isSpelAware;
    return this;
  }

  /** Adds additional validators */
  public withValidators(...validators: IValidator[]): FormValidatorField {
    this.validators = this.validators.concat(validators);
    return this;
  }
}
