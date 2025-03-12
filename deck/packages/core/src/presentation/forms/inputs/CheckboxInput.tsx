import classNames from 'classnames';
import React from 'react';

import type { IFormInputProps, OmitControlledInputPropsFrom } from './interface';
import { orEmptyString, validationClassName } from './utils';

interface ICheckBoxInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
  wrapperClassName?: string;
  text?: React.ReactNode;
}

const Nbsp = () => <>&nbsp;</>;

export class CheckboxInput extends React.Component<ICheckBoxInputProps> {
  public render() {
    const { value, validation, inputClassName, text, wrapperClassName, ...otherProps } = this.props;
    const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    return (
      <div className={classNames('checkbox', wrapperClassName)}>
        <label>
          <input className={className} type="checkbox" checked={!!value} {...otherProps} />
          {text ? text : <Nbsp />}
        </label>
      </div>
    );
  }
}
