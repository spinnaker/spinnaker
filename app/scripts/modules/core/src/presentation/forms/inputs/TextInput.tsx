import React from 'react';

import { IFormInputProps, OmitControlledInputPropsFrom } from './interface';
import { orEmptyString, validationClassName } from './utils';

export interface ITextInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
  prefix?: string;
}

export class TextInput extends React.Component<ITextInputProps> {
  public render() {
    const { value, validation, inputClassName, prefix, ...otherProps } = this.props;
    const className = `TextInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;
    const inputTag = (
      <input className={className} type="text" autoComplete="off" value={orEmptyString(value)} {...otherProps} />
    );

    if (prefix) {
      return (
        <div className="flex-container-h middle">
          <div className="sp-padding-xs-right body-regular">{prefix}</div>
          {inputTag}
        </div>
      );
    } else {
      return inputTag;
    }
  }
}
