import { get, set } from 'lodash';

// Use Formik's Field.validate() api https://jaredpalmer.com/formik/docs/api/field#validate
export type ValidatorResultRaw = undefined | string;
export type ValidatorResult = ValidatorResultRaw | Promise<ValidatorResultRaw>;
export type Validator = (value: any, label?: string) => ValidatorResult;
export type ArrayItemValidator = (
  itemBuilder: IArrayItemValidationBuilder,
  item: any,
  index: number,
  array: any[],
  arrayLabel: string,
) => void;
export type ValidatorFactory = (...args: any) => Validator;

export interface IValidationBuilder {
  field: (name: string, label: string) => IValidatableField;
  result: () => any | Promise<any>;
  arrayForEach: (iteratee: ArrayItemValidator) => Validator;
}

interface INamedValidatorResult {
  name: string;
  error: string;
}

interface IValidatableField {
  validate: (validators: Validator[]) => undefined | Promise<INamedValidatorResult>;
}

interface IArrayItemValidationBuilder extends IValidationBuilder {
  item: (label: string) => IValidatableField;
}

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

  public static oneOf = (list: any[], message?: string): Validator => {
    return (val: any, label?: string) => {
      list = list || [];
      message = message || `${label || 'This field'} must be one of (${list.join(', ')})`;
      return !list.includes(val) && message;
    };
  };

  public static skipIfUndefined = (actualValidator: Validator) => {
    return (val: any, label?: string) => {
      return val !== undefined && actualValidator(val, label);
    };
  };
}

const throwIfPromise = (maybe: any, message: string) => {
  if (maybe && typeof maybe.then === 'function') {
    throw new Error(message);
  }
  return maybe;
};

const validateSync = (validators: Validator[], value: any, label: string, name: string) => {
  const error = validators.reduce(
    (result, next) => (result ? result : next(value, label)),
    '', // Need a falsy ValidatorResult other than undefined, which will trip out Array.reduce()
  );
  return throwIfPromise(
    error,
    `Synchronous validator cannot return a Promise (while validating ${name}). Use buildValidatorsAsync(values) instead.`,
  );
};

const chainAsyncValidators = (validators: Validator[], value: any, label: string) => {
  return validators.reduce((p, next) => {
    return p.then((result: ValidatorResult) => (result ? result : next(value, label)));
  }, Promise.resolve(undefined));
};

const expandErrors = (errors: INamedValidatorResult[], isArray: boolean) => {
  return errors.reduce((acc, curr) => set(acc, curr.name, curr.error), isArray ? [] : {});
};

// This allows the error aggregation to ignore nested non-errors (i.e. [] or {})
const isError = (maybeError: any): boolean => {
  if (!maybeError) {
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

const buildValidatorsSync = (values: any): IValidationBuilder => {
  const isArray = Array.isArray(values);
  const synchronousErrors: INamedValidatorResult[] = [];
  return {
    field(name: string, label: string): IValidatableField {
      const value = get(values, name);
      return {
        validate(validators: Validator[]): undefined {
          const error = validateSync(validators, value, label, name);
          synchronousErrors.push(isError(error) && { name, error });
          return undefined;
        },
      };
    },
    result(): any {
      return expandErrors(synchronousErrors.filter(x => !!x), isArray);
    },
    arrayForEach(iteratee: ArrayItemValidator) {
      return arrayForEach(buildValidatorsSync, iteratee);
    },
  };
};

export const buildValidatorsAsync = (values: any): IValidationBuilder => {
  const isArray = Array.isArray(values);
  const promises: Array<Promise<INamedValidatorResult>> = [];
  return {
    field(name, label) {
      const value = get(values, name);
      return {
        validate(validators): Promise<INamedValidatorResult> {
          const chain: Promise<INamedValidatorResult> = chainAsyncValidators(validators, value, label)
            // We need to catch and resolve internal rejections because we'll be aggregating them using Promise.all() which fails fast
            // and only rejects the first rejection.
            .catch(error =>
              throwIfPromise(
                error,
                `Warning: caught nested Promise while validating ${name}. Async Validators should only be rejecting undefined or string, not Promises.`,
              ),
            )
            .then(error => isError(error) && { name, error });
          promises.push(chain);
          return chain;
        },
      };
    },
    result(): Promise<any> {
      return (
        Promise.all(promises)
          // we don't need to catch() here because internal promises are individually caught inside validate()
          .then(maybeErrors => {
            const actuallyErrors = maybeErrors.filter(x => !!x);
            if (actuallyErrors.length) {
              return Promise.reject(expandErrors(actuallyErrors, isArray));
            } else {
              return Promise.resolve({});
            }
          })
      );
    },
    arrayForEach(iteratee: ArrayItemValidator) {
      return arrayForEach(buildValidatorsAsync, iteratee);
    },
  };
};

export const buildValidators = (values: any, async?: boolean): IValidationBuilder => {
  return async ? buildValidatorsAsync(values) : buildValidatorsSync(values);
};

const createItemBuilder = (arrayBuilder: IValidationBuilder, index: number): IArrayItemValidationBuilder => {
  return {
    item(itemLabel) {
      return arrayBuilder.field(`[${index}]`, itemLabel);
    },
    field(name, itemLabel) {
      return arrayBuilder.field(`[${index}]${name}`, itemLabel);
    },
    result: arrayBuilder.result,
    arrayForEach: arrayBuilder.arrayForEach,
  };
};

// Utility to provide a builder for array items. The provided iteratee will be invoked for every array item.
const arrayForEach = (builder: (values: any) => IValidationBuilder, iteratee: ArrayItemValidator) => {
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
    return arrayBuilder.result();
  };
};

export const composeValidators = (validators: Validator[]): Validator => {
  const validatorList = validators.filter(x => !!x);
  if (!validatorList.length) {
    return null;
  } else if (validatorList.length === 1) {
    return validatorList[0];
  }

  const composedValidators: Validator = (value: any, label?: string) => {
    const results: ValidatorResult[] = validatorList.map(validator => Promise.resolve(validator(value, label)));

    // Return the first error returned from a validator
    // Or return the first rejected promise (thrown/rejected by an async validator)
    return Promise.all(results)
      .then((errors: string[]) => errors.find(error => !!error))
      .catch((error: string) => error);
  };

  return composedValidators;
};
