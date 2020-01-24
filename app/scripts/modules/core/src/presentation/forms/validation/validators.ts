import { IValidator } from './validation';
import { isNumber } from 'lodash';
import { robotToHuman } from 'core';

const THIS_FIELD = 'This field';

const emailValue = (message?: string): IValidator => {
  return (val: string, label = THIS_FIELD) => {
    message = message || `${label} is not a valid email address.`;
    return val && !/^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}$/i.test(val) && message;
  };
};

const isRequired = (message?: string): IValidator => {
  return (val: any, label = THIS_FIELD) => {
    message = message || `${label} is required.`;
    return (val === undefined || val === null || val === '') && message;
  };
};

const isNum = (message?: string) => (value: any) => (isNumber(value) ? null : message || 'Must be a number');

const minValue = (min: number, message?: string): IValidator => {
  return (val: number, label = THIS_FIELD) => {
    if (!isNumber(val)) {
      return message || `${label} must be a number`;
    } else if (val < min) {
      const minText = min === 0 ? 'cannot be negative' : `cannot be less than ${min}`;
      return message || `${label} ${minText}`;
    }
    return null;
  };
};

const maxValue = (max: number, message?: string): IValidator => {
  return (val: number, label = THIS_FIELD) => {
    if (!isNumber(val)) {
      return message || `${label} must be a number`;
    } else if (val > max) {
      const maxText = `cannot be greater than ${max}`;
      return message || `${label} ${maxText}`;
    }
    return null;
  };
};

const checkBetween = (fieldName: string, min: number, max: number): IValidator => (value: string) => {
  const sanitizedField = Number.parseInt(value, 10);

  if (!Number.isNaN(sanitizedField)) {
    const error =
      Validators.minValue(min)(sanitizedField, robotToHuman(fieldName)) ||
      Validators.maxValue(max)(sanitizedField, robotToHuman(fieldName));

    return error;
  }
  return null;
};

const oneOf = (list: any[], message?: string): IValidator => {
  return (val: any, label = THIS_FIELD) => {
    list = list || [];
    message = message || `${label} must be one of (${list.join(', ')})`;
    return !list.includes(val) && message;
  };
};

const arrayNotEmpty = (message?: string): IValidator => {
  return (val: string | any[], label = THIS_FIELD) => {
    message = message || `${label} must contain at least one entry`;
    return val && val.length < 1 && message;
  };
};

const skipIfUndefined = (actualValidator: IValidator): IValidator => {
  return (val: any, label = THIS_FIELD) => {
    return val !== undefined && actualValidator(val, label);
  };
};

const valueUnique = (list: any[], message?: string): IValidator => {
  return (val: any, label = THIS_FIELD) => {
    list = list || [];
    message = message || `${label} must be not be included in (${list.join(', ')})`;
    return list.includes(val) && message;
  };
};

/**
 * A collection of reusable Validator factories.
 *
 * ex: Validators.isRequired('You have to provide a value')
 * ex: Validators.minValue(0)
 * ex: Validators.maxValue(65534, 'You cant do that!')
 */
export const Validators = {
  arrayNotEmpty,
  emailValue,
  isRequired,
  isNum,
  maxValue,
  minValue,
  checkBetween,
  oneOf,
  skipIfUndefined,
  valueUnique,
};

// Typescript kludge:
// check that all keys of validators are factory functions that return an IValidator without typing validators explicitly
function _kludgeTypecheck(): { [key: string]: (...args: any[]) => IValidator } {
  return Validators;
}
_kludgeTypecheck();
