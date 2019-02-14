import { get, set } from 'lodash';

// Use Formik's Field.validate() api https://jaredpalmer.com/formik/docs/api/field#validate
export type IValidatorResultRaw = undefined | string;
export type IValidatorResult = IValidatorResultRaw | Promise<IValidatorResultRaw>;
export type IValidator = (value: any, label?: string) => IValidatorResult;
export type IArrayItemValidator = (
  itemBuilder: IArrayItemValidationBuilder,
  item: any,
  index: number,
  array: any[],
  arrayLabel: string,
) => void;

export interface IValidationBuilder {
  field: (name: string, label: string) => IValidatableField;
  result: () => any | Promise<any>;
  arrayForEach: (iteratee: IArrayItemValidator) => IValidator;
}

interface INamedValidatorResult {
  name: string;
  error: string;
}

interface IValidatableField {
  required: (validators?: IValidator[]) => undefined | Promise<INamedValidatorResult>;
  optional: (validators?: IValidator[]) => undefined | Promise<INamedValidatorResult>;
}

interface IArrayItemValidationBuilder extends IValidationBuilder {
  item: (label: string) => IValidatableField;
}

const throwIfPromise = (maybe: any, message: string) => {
  if (maybe && typeof maybe.then === 'function') {
    throw new Error(message);
  }
  return maybe;
};

const validateSync = (validators: IValidator[], value: any, label: string, name: string) => {
  const error = validators.reduce(
    (result, next) => (result ? result : next(value, label)),
    '', // Need a falsy ValidatorResult other than undefined, which will trip out Array.reduce()
  );
  return throwIfPromise(
    error,
    `Synchronous validator cannot return a Promise (while validating ${name}). Use buildValidatorsAsync(values) instead.`,
  );
};

const chainAsyncValidators = (validators: IValidator[], value: any, label: string) => {
  return validators.reduce((p, next) => {
    return p.then((result: IValidatorResult) => (result ? result : next(value, label)));
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

const createItemBuilder = (arrayBuilder: IValidationBuilder, index: number): IArrayItemValidationBuilder => {
  return {
    item(itemLabel) {
      return arrayBuilder.field(`[${index}]`, itemLabel);
    },
    field(name, itemLabel) {
      return arrayBuilder.field(`[${index}].${name}`, itemLabel);
    },
    result: arrayBuilder.result,
    arrayForEach: arrayBuilder.arrayForEach,
  };
};

// Utility to provide a builder for array items. The provided iteratee will be invoked for every array item.
const arrayForEach = (builder: (values: any) => IValidationBuilder, iteratee: IArrayItemValidator) => {
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

const buildValidatorsSync = (values: any): IValidationBuilder => {
  const isArray = Array.isArray(values);
  const synchronousErrors: INamedValidatorResult[] = [];
  return {
    field(name: string, label: string): IValidatableField {
      const value = get(values, name);
      return {
        required(validators = [], message?: string): undefined {
          if (value === undefined || value === null || value === '') {
            message = message || `${label} is required.`;
            synchronousErrors.push({ name, error: message });
            return undefined;
          }
          return this.optional(validators);
        },
        optional(validators: IValidator[]): undefined {
          if (value === undefined || value === null || value === '') {
            // Don't run validation on an undefined/null/empty
            return undefined;
          }
          const error = validateSync(validators, value, label, name);
          synchronousErrors.push(isError(error) && { name, error });
          return undefined;
        },
      };
    },
    result(): any {
      return expandErrors(synchronousErrors.filter(x => !!x), isArray);
    },
    arrayForEach(iteratee: IArrayItemValidator) {
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
        required(validators = [], message?: string): Promise<INamedValidatorResult> {
          if (value === undefined || value === null || value === '') {
            message = message || `${label} is required.`;
            const chain = Promise.resolve({ name, error: message });
            promises.push(chain);
            return chain;
          }
          return this.optional(validators);
        },
        optional(validators: IValidator[]): Promise<INamedValidatorResult> {
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
    arrayForEach(iteratee: IArrayItemValidator) {
      return arrayForEach(buildValidatorsAsync, iteratee);
    },
  };
};

export const buildValidators = (values: any, async?: boolean): IValidationBuilder => {
  return async ? buildValidatorsAsync(values) : buildValidatorsSync(values);
};

export const composeValidators = (validators: IValidator[]): IValidator => {
  const validatorList = validators.filter(x => !!x);
  if (!validatorList.length) {
    return null;
  } else if (validatorList.length === 1) {
    return validatorList[0];
  }

  const composedValidators: IValidator = (value: any, label?: string) => {
    const results: IValidatorResult[] = validatorList.map(validator => Promise.resolve(validator(value, label)));

    // Return the first error returned from a validator
    // Or return the first rejected promise (thrown/rejected by an async validator)
    return Promise.all(results)
      .then((errors: string[]) => errors.find(error => !!error))
      .catch((error: string) => error);
  };

  return composedValidators;
};
