import { isString } from 'lodash';
import React from 'react';

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

    if (React.isValidElement(validationMessage)) {
      return { category: null, messageNode: validationMessage, hidden: false };
    } else if (isString(validationMessage)) {
      const [category, messageNode] = categorizeValidationMessage(validationMessage as string);

      const isErrorOrWarning = category === 'error' || category === 'warning';
      const hidden = !touched && isErrorOrWarning;

      return { category, messageNode, hidden };
    }

    return { category: null, messageNode: validationMessage, hidden: true };
  }, [validationMessage, touched]);
}
