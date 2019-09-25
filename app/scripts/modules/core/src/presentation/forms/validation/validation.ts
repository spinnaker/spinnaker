import { get, isBoolean, set, startCase } from 'lodash';
import { Validators } from './validators';

// Use Formik's Field.validate() api https://jaredpalmer.com/formik/docs/api/field#validate
export type IValidatorResult = undefined | string;
export type IValidator = (value: any, label?: string) => IValidatorResult;
export type IArrayItemValidator = (
  itemBuilder: IArrayItemValidationBuilder,
  item: any,
  index: number,
  array: any[],
  arrayLabel: string,
) => void;

export interface IFormValidator {
  /**
   * Defines a new form field to validate
   *
   * @param name the name of the field in the Formik Form
   * @param label (optional) the label of this field.
   */
  field: (name: string, label: string) => IValidatableField;

  /**
   * Runs validation on all the ValidatableField(s) in this FormValidator.
   *
   * This function aggregate all the field validation errors into an object compatible with Formik Errors.
   * Each field error is stored in the resulting object using the field's 'name' as a path.
   */
  validateForm: () => any;
  arrayForEach: (iteratee: IArrayItemValidator) => IValidator;
}

interface IValidatableField {
  /** Causes the field to fail validation if the value is undefined, null, or empty string. */
  required(message?: string): ValidatableField;

  /**
   * Causes the field to pass validation if the value is undefined, null, or empty string.
   * Fields are default by default.
   */
  optional(): ValidatableField;

  /** Causes the field to pass validation if the value contains SpEL */
  spelAware(isSpelAware?: boolean): ValidatableField;

  /** Adds additional validators */
  withValidators(...validators: IValidator[]): ValidatableField;
}

export const FORM_VALIDATION_VALIDATABLE_FIELD_IS_VALID_SHORT_CIRCUIT = '__FIELD_IS_VALID_SHORT_CIRCUIT__';

interface INamedValidatorResult {
  name: string;
  error: string;
}

interface IArrayItemValidationBuilder extends IFormValidator {
  item: (label: string) => IValidatableField;
}

const runValidators = (validators: IValidator[], value: any, label: string) => {
  return validators.reduce(
    (result, next) => (result ? result : next(value, label)),
    '', // Need a falsy ValidatorResult other than undefined, which will trip out Array.reduce()
  );
};

/** Transforms a list of INamedValidatorResults into a Formik-compatible errors object */
const expandErrors = (errors: INamedValidatorResult[], isArray: boolean) => {
  return errors.reduce((acc, curr) => set(acc, curr.name, curr.error), isArray ? [] : {});
};

// This allows the error aggregation to ignore nested non-errors (i.e. [] or {})
const isError = (maybeError: any): boolean => {
  if (!maybeError) {
    return false;
  } else if (maybeError === FORM_VALIDATION_VALIDATABLE_FIELD_IS_VALID_SHORT_CIRCUIT) {
    return false;
  } else if (typeof maybeError === 'string') {
    return true;
  } else if (Array.isArray(maybeError)) {
    return !!maybeError.length;
  } else if (typeof maybeError === 'object') {
    return !!Object.keys(maybeError).length;
  }
  return !!maybeError;
};

const createItemBuilder = (arrayBuilder: IFormValidator, index: number): IArrayItemValidationBuilder => {
  return {
    item(itemLabel) {
      return arrayBuilder.field(`[${index}]`, itemLabel);
    },
    field(name, itemLabel) {
      return arrayBuilder.field(`[${index}].${name}`, itemLabel);
    },
    validateForm: arrayBuilder.validateForm,
    arrayForEach: arrayBuilder.arrayForEach,
  };
};

// Utility to provide a builder for array items. The provided iteratee will be invoked for every array item.
const arrayForEach = (builder: (values: any) => IFormValidator, iteratee: IArrayItemValidator) => {
  return (array: any[], arrayLabel?: string) => {
    // Silently ignore non-arrays (usually undefined). If strict type checking is desired, it should be done by a previous validator.
    if (!Array.isArray(array)) {
      return false;
    }
    const arrayBuilder = builder(array);
    array.forEach((item: any, index: number) => {
      const itemBuilder = createItemBuilder(arrayBuilder, index);
      iteratee && iteratee(itemBuilder, item, index, array, arrayLabel);
    });
    return arrayBuilder.validateForm();
  };
};

export class FormValidator implements IFormValidator {
  constructor(private values: any) {}
  private fields: ValidatableField[] = [];
  private isSpelAwareDefault = false;

  /**
   * Defines a new form field to validate
   *
   * @param name the name of the field in the Formik Form
   * @param label (optional) the label of this field.
   * If no label is provided, it will be inferred based on the name.
   */
  public field(name: string, label?: string): IValidatableField {
    label = label || startCase(name);

    const field = new ValidatableField(name, label);
    this.fields.push(field);
    return field;
  }

  /**
   * Makes this FormValidator default to being spel-aware or not
   *
   * When true, all fields in this FormValidator will default to allowing spel values to pass validation.
   * Individual fields may override this default by calling field().spelAware()
   */
  public spelAware(isSpelAware = true): FormValidator {
    this.isSpelAwareDefault = isSpelAware;
    return this;
  }

  private getFieldValidators(field: ValidatableField, isSpelAwareDefault: boolean): IValidator[] {
    const isSpelAware = isBoolean(field.isSpelAware) ? field.isSpelAware : isSpelAwareDefault;

    const requiredValidator = field.isRequired ? Validators.isRequired(field.isRequiredMessage) : null;
    const optionalValidator = !field.isRequired ? isOptionalValidator() : null;
    const spelValidator = isSpelAware ? spelAwareValidator() : null;

    return [requiredValidator, optionalValidator, spelValidator, ...field.validators].filter(x => !!x);
  }

  /**
   * This runs validation on all the ValidatableField(s) in this FormValidator.
   *
   * It aggregate all the field validation errors into an object compatible with Formik Errors.
   * Each field error is stored in the resulting object using the field's 'name' as a path.
   */
  public validateForm(): any {
    const results: INamedValidatorResult[] = this.fields.map(field => {
      const { name, label } = field;

      const value = get(this.values, name);
      const fieldValidators = this.getFieldValidators(field, this.isSpelAwareDefault);
      const error = runValidators(fieldValidators, value, label);

      return { name, error };
    });

    const errors = results.filter(fieldResult => isError(fieldResult.error));

    return expandErrors(errors, Array.isArray(this.values));
  }

  public arrayForEach(iteratee: IArrayItemValidator): any {
    return arrayForEach(values => new FormValidator(values), iteratee);
  }
}

/**
 * Encapsulates a form field and the validation rules defined for that field.
 *
 * By default a ValidatableField is optional.
 */
class ValidatableField implements IValidatableField {
  constructor(public name: string, public label: string) {}
  public isSpelAware: boolean;
  public isRequired: boolean;
  public isRequiredMessage: string;
  public validators: IValidator[] = [];

  /** Causes the field to fail validation if the value is undefined, null, or empty string. */
  public required(message?: string): ValidatableField {
    this.isRequired = true;
    this.isRequiredMessage = message;
    return this;
  }

  /**
   * Causes the field to pass validation if the value is undefined, null, or empty string.
   * Fields are default by default.
   */
  public optional(): ValidatableField {
    this.isRequired = false;
    this.isRequiredMessage = undefined;
    return this;
  }

  /** Causes the field to pass validation if the value contains SpEL */
  public spelAware(isSpelAware = true): ValidatableField {
    this.isSpelAware = isSpelAware;
    return this;
  }

  /** Adds additional validators */
  public withValidators(...validators: IValidator[]): ValidatableField {
    this.validators = this.validators.concat(validators);
    return this;
  }
}

/**
 * Not exported because it uses.short circuiting, so it only works inside of a FormValidator
 * Use via ValidatableField.optional().
 */
function isOptionalValidator(): IValidator {
  return value => {
    const isValueMissing = value === undefined || value === null || value === '';
    return isValueMissing ? FORM_VALIDATION_VALIDATABLE_FIELD_IS_VALID_SHORT_CIRCUIT : null;
  };
}

/**
 * Not exported because it uses.short circuiting, so it only works inside of a FormValidator
 * Use via ValidatableField.spelAware().
 */
function spelAwareValidator(): IValidator {
  return value => {
    const isSpelContent = typeof value === 'string' && value.includes('${');
    return isSpelContent ? FORM_VALIDATION_VALIDATABLE_FIELD_IS_VALID_SHORT_CIRCUIT : null;
  };
}

export const composeValidators = (validators: IValidator[]): IValidator => {
  const validatorList = validators.filter(x => !!x);
  if (!validatorList.length) {
    return null;
  } else if (validatorList.length === 1) {
    return validatorList[0];
  }

  const composedValidators: IValidator = (value: any, label?: string) => {
    const results: IValidatorResult[] = validatorList.map(validator => validator(value, label));
    // Return the first error returned from a validator
    return results.find(error => !!error);
  };

  return composedValidators;
};
