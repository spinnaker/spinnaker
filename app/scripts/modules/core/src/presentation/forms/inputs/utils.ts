import * as classNames from 'classnames';
import { isUndefined, isString } from 'lodash';

import { noop } from 'core/utils';

import { IValidationProps } from '../interface';

export const orEmptyString = (val: any) => (isUndefined(val) ? '' : val);

export const validationClassName = (validation: IValidationProps) => {
  validation = validation || {};
  return classNames({
    'ng-dirty': !!validation.touched,
    'ng-invalid': validation.validationStatus === 'error',
    'ng-warning': validation.validationStatus === 'warning',
  });
};

export const createFakeReactSyntheticEvent = (target: { name?: string; value?: any }) =>
  ({
    persist: noop,
    stopPropagation: noop,
    preventDefault: noop,
    target,
  } as React.ChangeEvent<any>);

export const isStringArray = (opts: any[]): opts is string[] => opts && opts.length && opts.every(isString);
