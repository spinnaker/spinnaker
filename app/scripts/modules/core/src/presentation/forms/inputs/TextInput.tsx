import * as React from 'react';

import { orEmptyString, validationClassName } from './utils';
import { IFormInputProps, OmitControlledInputPropsFrom } from '../interface';

interface ITextInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  inputClassName?: string;
}

export class TextInput extends React.Component<ITextInputProps> {
  public render() {
    const { value, validation, inputClassName, ...otherProps } = this.props;
    const className = `TextInput form-control ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;
    return <input className={className} type="text" autoComplete="off" value={orEmptyString(value)} {...otherProps} />;
  }
}
