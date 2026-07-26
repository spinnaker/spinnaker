import classNames from 'classnames';
import { isNil, isString } from 'lodash';

import type { IFormInputValidation } from './interface';
import { noop } from '../../../utils';

export const orEmptyString = (val: any) => (isNil(val) ? '' : val);

export const validationClassName = (validation = {} as IFormInputValidation) => {
  const touched = !!validation.touched;
  return classNames({
    dirty: touched,
    invalid: touched && validation.category === 'error',
    warning: touched && validation.category === 'warning',
  });
};

export const createFakeReactSyntheticEvent = (target: { name?: string; value?: any }) =>
  ({
    persist: noop,
    stopPropagation: noop,
    preventDefault: noop,
    target,
  } as React.ChangeEvent<any>);

export const isStringArray = (opts: readonly any[]): opts is string[] => opts && opts.length && opts.every(isString);
