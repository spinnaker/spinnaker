import React from 'react';

import { IFormInputProps, OmitControlledInputPropsFrom } from './interface';
import { orEmptyString, validationClassName } from './utils';

interface ICheckBoxInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
  text?: React.ReactNode;
}

const Nbsp = () => <>&nbsp;</>;

export class CheckboxInput extends React.Component<ICheckBoxInputProps> {
  public render() {
    const { value, validation, inputClassName, text, ...otherProps } = this.props;
    const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

    return (
      <div className="checkbox">
        <label>
          <input className={className} type="checkbox" checked={!!value} {...otherProps} />
          {text ? text : <Nbsp />}
        </label>
      </div>
    );
  }
}
