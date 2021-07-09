import { set, values } from 'lodash';
import { traverseObject } from '../../../utils';

export const categoryLabels = {
  async: 'Async',
  error: 'Error',
  info: 'Info',
  message: 'Message',
  success: 'Success',
  warning: 'Warning',
};

type ICategoryLabels = typeof categoryLabels;
export type IValidationCategory = keyof typeof categoryLabels;
export type ICategorizedErrors = {
  [P in keyof ICategoryLabels]: any;
};

// Category label strings, e.g., ['Error', 'Warning', ...]
const labels = values(categoryLabels);
const statusKeys = Object.keys(categoryLabels);
const inverseLabels: { [label: string]: IValidationCategory } = Object.keys(categoryLabels).reduce(
  (acc, key: IValidationCategory) => ({ ...acc, [categoryLabels[key]]: key }),
  {},
);

const buildCategoryMessage = (type: IValidationCategory) => (message: string) => {
  return message ? `${categoryLabels[type]}: ${message}` : null;
};

export const asyncMessage = buildCategoryMessage('async');
export const errorMessage = buildCategoryMessage('error');
export const infoMessage = buildCategoryMessage('info');
export const messageMessage = buildCategoryMessage('message');
export const successMessage = buildCategoryMessage('success');
export const warningMessage = buildCategoryMessage('warning');

// A regular expression which captures the category label and validation message from a validation message
// I.e., for the string: "Error: There was a fatal error"
// this captures "Error" and "There was a fatal error"
const validationMessageRegexp = new RegExp(`^(${labels.join('|')}): ((?:[\r\n]|.)*)$`, 'm');

// Takes an errorMessage with embedded category and extracts the category and message
// Example:  "Error: there was an error" => ['error', 'there was an error']
// Example:  "this message has no explicit category" => ['error', 'this message has no explicit category']
export const categorizeValidationMessage = (validationMessage: string): [IValidationCategory, string] => {
  if (!validationMessage) {
    return [null, null];
  }

  const result = validationMessageRegexp.exec(validationMessage);
  if (!result) {
    // If no known category label was found embedded in the error message, default the category to 'error'
    return ['error', validationMessage];
  }
  const [label, message] = result.slice(1);
  const status = inverseLabels[label];

  return [status, message];
};

/** Organizes errors from an errors object into ICategorizedErrors buckets. */
export const categorizeValidationMessages = (errors: any): ICategorizedErrors => {
  // Build an empty Categorized Errors object
  const categories: ICategorizedErrors = statusKeys.reduce((acc, status) => ({ ...acc, [status]: {} }), {}) as any;

  // Given a path and a validation message, store the validation message into the same path of the correct category
  const storeMessageInCategory = (path: string, validationMessage: any) => {
    const [status, message] = categorizeValidationMessage(validationMessage);

    if (message) {
      set(categories[status], path, message);
    }
  };

  traverseObject(errors, storeMessageInCategory, true);

  return categories;
};
