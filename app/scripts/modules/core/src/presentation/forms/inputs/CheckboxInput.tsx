import * as React from 'react';

import { orEmptyString, validationClassName } from './utils';
import { IFormInputProps, OmitControlledInputPropsFrom } from '../interface';

interface ICheckBoxInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
  text?: React.ReactNode;
}

export class CheckboxInput extends React.Component<ICheckBoxInputProps> {
  public render() {
    const { value, validation, inputClassName, text, ...otherProps } = this.props;
    const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    return (
      <div className="checkbox">
        <label>
          <input className={className} type="checkbox" checked={!!value} {...otherProps} />
          {text}
        </label>
      </div>
    );
  }
}
