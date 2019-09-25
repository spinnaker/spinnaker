import { get, set } from 'lodash';

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

export interface IValidationBuilder {
  field: (name: string, label: string) => IValidatableField;
  result: () => any;
  arrayForEach: (iteratee: IArrayItemValidator) => IValidator;
}

interface INamedValidatorResult {
  name: string;
  error: string;
}

interface IValidatableField {
  required: (validators?: IValidator[]) => undefined;
  optional: (validators?: IValidator[]) => undefined;
}

interface IArrayItemValidationBuilder extends IValidationBuilder {
  item: (label: string) => IValidatableField;
}

const runValidators = (validators: IValidator[], value: any, label: string) => {
  return validators.reduce(
    (result, next) => (result ? result : next(value, label)),
    '', // Need a falsy ValidatorResult other than undefined, which will trip out Array.reduce()
  );
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

export const buildValidators = (values: any): IValidationBuilder => {
  const isArray = Array.isArray(values);
  const errors: INamedValidatorResult[] = [];
  return {
    field(name: string, label: string): IValidatableField {
      const value = get(values, name);
      return {
        required(validators = [], message?: string): undefined {
          if (value === undefined || value === null || value === '') {
            message = message || `${label} is required.`;
            errors.push({ name, error: message });
            return undefined;
          }
          return this.optional(validators);
        },
        optional(validators: IValidator[]): undefined {
          if (value === undefined || value === null || value === '') {
            // Don't run validation on an undefined/null/empty
            return undefined;
          }
          const error = runValidators(validators, value, label);
          errors.push(isError(error) && { name, error });
          return undefined;
        },
      };
    },
    result(): any {
      return expandErrors(errors.filter(x => !!x), isArray);
    },
    arrayForEach(iteratee: IArrayItemValidator) {
      return arrayForEach(buildValidators, iteratee);
    },
  };
};

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
