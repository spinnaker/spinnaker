// Use Formik's Field.validate() api https://jaredpalmer.com/formik/docs/api/field#validate
export type ValidatorResult = undefined | string | Promise<any>;
export type Validator = (value: any, label?: string) => ValidatorResult;
export type ValidatorFactory = (...args: any) => Validator;

/**
 * A collection of reusable Validator factories.
 *
 * ex: Validators.isRequired('You have to provide a value')
 * ex: Validators.minValue(0)
 * ex: Validators.maxValue(65534, 'You cant do that!')
 */
export class Validation {
  public static isRequired: ValidatorFactory = (message?: string) => {
    return (val: any, label?: string) => {
      message = message || `${label || 'This field'} is required.`;
      return (val === undefined || val === null || val === '') && message;
    };
  };

  public static minValue: ValidatorFactory = (minValue: number, message?: string) => {
    return (val: number, label?: string) => {
      const validationText = minValue === 0 ? 'cannot be negative' : `cannot be less than ${minValue}`;
      message = message || `${label || 'This field'} ${validationText}`;
      return val < minValue && message;
    };
  };

  public static maxValue: ValidatorFactory = (maxValue: number, message?: string) => {
    return (val: number, label?: string) => {
      message = message || `${label || 'This field'} cannot be greater than ${maxValue}`;
      return val > maxValue && message;
    };
  };
}

export const composeValidators = (...validators: Validator[]): Validator => {
  const composedValidators: Validator = (value: any, label?: string) => {
    const validatorResults: ValidatorResult[] = validators.map(validator => Promise.resolve(validator(value, label)));

    // Return the first error returned from a validator
    // Or return the first rejected promise (thrown/rejected by an async validator)
    return Promise.all(validatorResults)
      .then((errors: string[]) => {
        return errors.find(error => !!error);
      })
      .catch((error: string) => error);
  };

  return composedValidators;
};
