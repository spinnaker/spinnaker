import * as React from 'react';
import { isString } from 'lodash';
import { categorizeValidationMessage, IValidationCategory } from './categories';

export interface IValidationData {
  category: IValidationCategory | undefined;
  messageNode: React.ReactNode;
  hidden: boolean;
}

/**
 * Encapsulates processing of a validationMessage for use within a form field.
 *
 * 1) Extracts the validation message and category from a validationMessage (see categories.ts)
 * 2) Determines the visiblity of the validation message (errors and warnings are visible when the field is touched)
 *
 * @returns { category, messageNode, hidden }
 */
export function useValidationData(validationMessage: React.ReactNode, touched: boolean): IValidationData {
  return React.useMemo(() => {
    if (!validationMessage) {
      return { category: null, messageNode: null, hidden: true };
    }

    if (!isString(validationMessage)) {
      return { category: null, messageNode: validationMessage, hidden: false };
    }

    const [category, messageNode] = categorizeValidationMessage(validationMessage);

    const isErrorOrWarning = category === 'error' || category === 'warning';
    const hidden = !touched && isErrorOrWarning;

    return { category, messageNode, hidden };
  }, [validationMessage, touched]);
}
