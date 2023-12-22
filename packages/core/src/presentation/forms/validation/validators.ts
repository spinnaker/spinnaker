import { isNumber } from 'lodash';

import { SETTINGS } from '../../../config/settings';
import { robotToHuman } from '../../robotToHumanFilter/robotToHuman.filter';
import type { IValidator } from './validation';

const THIS_FIELD = 'This field';
// RCF 5322 Email Validation Regex taken from https://emailregex.com/
const VALID_EMAIL_REGEX = new RegExp(
  '^(([^<>()\\[\\]\\\\.,;:\\s@"]+(\\.[^<>()\\[\\]\\\\.,;:\\s@"]+)*)|(".+"))@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$',
);

const urlPattern = SETTINGS.cdevents?.validUrlPattern ?? '^https?://.+$';
const VALID_URL = new RegExp(urlPattern);

const cdeventPattern = SETTINGS.cdevents?.validCDEvent ?? '^dev\\.cdevents\\.[^.]+\\.[^.]+$';
const VALID_CDEVENT_REGEX = new RegExp(cdeventPattern);

const emailValue = (message?: string): IValidator => {
  return function emailValue(val: string, label = THIS_FIELD) {
    message = message || `${label} is not a valid email address.`;
    return val && !VALID_EMAIL_REGEX.test(val) && message;
  };
};

const urlValue = (message?: string): IValidator => {
  return function urlValue(val: string, label = THIS_FIELD) {
    message = message || `${label} is not a valid URL.`;
    return val && !VALID_URL.test(val) && message;
  };
};

const cdeventsTypeValue = (message?: string): IValidator => {
  return function cdeventsTypeValue(val: string, label = THIS_FIELD) {
    message = message || `${label} is not a valid CDEvents Type.`;
    return val && !VALID_CDEVENT_REGEX.test(val) && message;
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

const skipIfSpel = (actualValidator: IValidator): IValidator => {
  return function skipIfSpel(val: any, label = THIS_FIELD) {
    return typeof val === 'string' && val.includes('${') ? undefined : actualValidator(val, label);
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
  cdeventsTypeValue,
  urlValue,
  isNum,
  isRequired,
  isValidJson,
  isValidXml,
  maxValue,
  minValue,
  oneOf,
  skipIfUndefined,
  skipIfSpel,
  valueUnique,
};

// Typescript kludge:
// check that all keys of validators are factory functions that return an IValidator without typing validators explicitly
function _kludgeTypecheck(): { [key: string]: (...args: any[]) => IValidator } {
  return Validators;
}
_kludgeTypecheck();
