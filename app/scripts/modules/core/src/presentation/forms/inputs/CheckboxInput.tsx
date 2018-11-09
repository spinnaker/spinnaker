import * as React from 'react';

import { orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from '../interface';

interface ICheckBoxInputProps extends IFormInputProps, React.InputHTMLAttributes<any> {
  inputClassName?: string;
  text?: React.ReactNode;
}

export class CheckboxInput extends React.Component<ICheckBoxInputProps> {
  public render() {
    const { field, validation, inputClassName, text, ...otherProps } = this.props;
    const { value, ...fieldProps } = field;
    const className = `CheckboxInput ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    return (
      <div className="checkbox">
        <label>
          <input className={className} type="checkbox" checked={!!value} {...fieldProps} {...otherProps} />
          {text}
        </label>
      </div>
    );
  }
}
