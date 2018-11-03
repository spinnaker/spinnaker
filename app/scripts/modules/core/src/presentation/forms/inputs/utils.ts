import * as classNames from 'classnames';
import { isUndefined, isString } from 'lodash';

import { IValidationProps } from '../interface';

export const orEmptyString = (val: any) => (isUndefined(val) ? '' : val);

export const validationClassName = (validation: IValidationProps) => {
  return classNames({
    'ng-dirty': !!validation.touched,
    'ng-invalid': validation.validationStatus === 'error',
    'ng-warning': validation.validationStatus === 'warning',
  });
};

export const isStringArray = (opts: any[]): opts is string[] => opts && opts.length && opts.every(isString);
