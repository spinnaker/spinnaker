import { isNumber } from 'lodash';

import { robotToHuman } from '../../../index';
import { IValidator } from './validation';

const THIS_FIELD = 'This field';

const emailValue = (message?: string): IValidator => {
  return function emailValue(val: string, label = THIS_FIELD) {
    message = message || `${label} is not a valid email address.`;
    return val && !/^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}$/i.test(val) && message;
  };
};

const isRequired = (message?: string): IValidator => {
  return function isRequired(val: any, label = THIS_FIELD) {
    message = message || `${label} is required.`;
    return (val === undefined || val === null || val === '') && message;
  };
};

const isNum = (message?: string): IValidator => {
  return function isNum(value: any) {
    return isNumber(value) ? null : message || 'Must be a number';
  };
};

const minValue = (min: number, message?: string): IValidator => {
  return function minValue(val: number, label = THIS_FIELD) {
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
  return function maxValue(val: number, label = THIS_FIELD) {
    if (!isNumber(val)) {
      return message || `${label} must be a number`;
    } else if (val > max) {
      const maxText = `cannot be greater than ${max}`;
      return message || `${label} ${maxText}`;
    }
    return null;
  };
};

const checkBetween = (fieldName: string, min: number, max: number): IValidator => {
  return function checkBetween(value: string) {
    const sanitizedField = Number.parseInt(value, 10);

    if (!Number.isNaN(sanitizedField)) {
      const error =
        Validators.minValue(min)(sanitizedField, robotToHuman(fieldName)) ||
        Validators.maxValue(max)(sanitizedField, robotToHuman(fieldName));

      return error;
    }
    return null;
  };
};

const oneOf = (list: any[], message?: string): IValidator => {
  return function oneOf(val: any, label = THIS_FIELD) {
    list = list || [];
    message = message || `${label} must be one of (${list.join(', ')})`;
    return !list.includes(val) && message;
  };
};

const arrayNotEmpty = (message?: string): IValidator => {
  return function arrayNotEmpty(val: string | any[], label = THIS_FIELD) {
    message = message || `${label} must contain at least one entry`;
    return val && val.length < 1 && message;
  };
};

const skipIfUndefined = (actualValidator: IValidator): IValidator => {
  return function skipIfUndefined(val: any, label = THIS_FIELD) {
    return val !== undefined && actualValidator(val, label);
  };
};

const valueUnique = (list: any[], message?: string): IValidator => {
  return function valueUnique(val: any, label = THIS_FIELD) {
    list = list || [];
    message = message || `${label} must be not be included in (${list.join(', ')})`;
    return list.includes(val) && message;
  };
};

const isValidJson = (message?: string): IValidator => {
  return function isValidJson(val: string, label = 'this field') {
    try {
      JSON.parse(val);
    } catch (parseError) {
      message = message || `${label} must be valid JSON: ${parseError.message}`;
      return message;
    }
    return undefined;
  };
};

const isValidXml = (message?: string): IValidator => {
  return function isValidXml(val: string, label = 'this field') {
    const xmlDoc = new DOMParser().parseFromString(val, 'text/xml');
    const elements = xmlDoc.getElementsByTagName('parsererror');
    if (elements && elements.length > 0) {
      message = message || `${label} must be valid XML`;
      return message;
    }
    return undefined;
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
  checkBetween,
  emailValue,
  isNum,
  isRequired,
  isValidJson,
  isValidXml,
  maxValue,
  minValue,
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
