// Use Formik's Field.validate() api https://jaredpalmer.com/formik/docs/api/field#validate
export type IValidatorResult = undefined | string;
export type IValidator = (value: any, label?: string) => IValidatorResult;

export interface IFormValidator {
  /**
   * Defines a new form field to validate
   *
   * @param name the name of the field in the Formik Form
   * @param label (optional) the label of this field.
   */
  field: (name: string, label: string) => IFormValidatorField;

  /**
   * Runs validation on all the ValidatableField(s) in this FormValidator.
   *
   * This function aggregate all the field validation errors into an object compatible with Formik Errors.
   * Each field error is stored in the resulting object using the field's 'name' as a path.
   */
  validateForm: () => any;
  arrayForEach: (iteratee: IArrayItemValidator) => IValidator;
}

export interface IFormValidatorField {
  /** Causes the field to fail validation if the value is undefined, null, or empty string. */
  required(message?: string): IFormValidatorField;

  /**
   * Causes the field to pass validation if the value is undefined, null, or empty string.
   * Fields are default by default.
   */
  optional(): IFormValidatorField;

  /** Causes the field to pass validation if the value contains SpEL */
  spelAware(isSpelAware?: boolean): IFormValidatorField;

  /** Adds additional validators */
  withValidators(...validators: IValidator[]): IFormValidatorField;
}

export type IArrayItemValidator = (
  itemBuilder: IArrayItemValidationBuilder,
  item: any,
  index: number,
  array: any[],
  arrayLabel: string,
) => void;

export interface IArrayItemValidationBuilder extends IFormValidator {
  item: (label: string) => IFormValidatorField;
}

export const FORM_VALIDATION_VALIDATABLE_FIELD_IS_VALID_SHORT_CIRCUIT = '__FIELD_IS_VALID_SHORT_CIRCUIT__';

export const composeValidators = (validators: IValidator[]): IValidator => {
  const validatorList = validators.filter((x) => !!x);
  if (!validatorList.length) {
    return null;
  } else if (validatorList.length === 1) {
    return validatorList[0];
  }

  const composedValidators: IValidator = (value: any, label?: string) => {
    const results: IValidatorResult[] = validatorList.map((validator) => validator(value, label));
    // Return the first error returned from a validator
    return results.find((error) => !!error);
  };

  return composedValidators;
};
