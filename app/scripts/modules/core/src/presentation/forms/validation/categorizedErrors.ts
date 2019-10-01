import { set, values } from 'lodash';
import { traverseObject } from '../../../utils';

import { categoryLabels, ICategorizedErrors, IValidationCategory } from './validation';

// Category label strings, e.g., ['Error', 'Warning', ...]
const labels = values(categoryLabels);
const statusKeys = Object.keys(categoryLabels);
const inverseLabels: { [label: string]: IValidationCategory } = Object.keys(categoryLabels).reduce(
  (acc, key: IValidationCategory) => ({ ...acc, [categoryLabels[key]]: key }),
  {},
);

// A regular expression which captures the category label and validation message from a validation message
// I.e., for the string: "Error: There was a fatal error"
// this captures "Error" and "There was a fatal error"
const errorMessageRegexp = new RegExp(`^(${labels.join('|')}): (.*)$`);

// Takes an errorMessage with embedded category and extracts the category and message
// Example:  "Error: there was an error" => ['error', 'there was an error']
// Example:  "this message has no explicit category" => ['error', 'this message has no explicit category']
export const categorizeErrorMessage = (errorMessage: string): [IValidationCategory, string] => {
  const result = errorMessageRegexp.exec(errorMessage);
  if (!result) {
    // If no known category label was found embedded in the error message, default the category to 'error'
    return ['error', errorMessage];
  }
  const [label, message] = result.slice(1);
  const status = inverseLabels[label];

  return [status, message];
};

/** Organizes errors from an errors object into ICategorizedErrors buckets. */
export const categorizeErrors = (errors: any): ICategorizedErrors => {
  // Build an empty Categorized Errors object
  const categories: ICategorizedErrors = statusKeys.reduce((acc, status) => ({ ...acc, [status]: {} }), {}) as any;

  // Given a path and a validation message, store the validation message into the same path of the correct category
  const storeMessageInCategory = (path: string, errorMessage: any) => {
    const [status, message] = categorizeErrorMessage(errorMessage);

    if (message) {
      set(categories[status], path, message);
    }
  };

  traverseObject(errors, storeMessageInCategory, true);

  return categories;
};
