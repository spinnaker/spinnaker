import * as classNames from 'classnames';
import * as React from 'react';

import { IFormInputProps } from '../interface';

interface ITextInputProps extends IFormInputProps, React.InputHTMLAttributes<any> {
  inputClassName?: string;
}

export class TextInput extends React.Component<ITextInputProps> {
  public render() {
    const { field, validation, inputClassName, ...otherProps } = this.props;
    const fieldProps = { ...field, value: field.value || '' };

    const validationClassName = classNames({
      'form-control': true,
      'ng-dirty': validation.touched,
      'ng-invalid': validation.error,
    });

    return (
      <input
        className={`${inputClassName} ${validationClassName}`}
        type="text"
        autoComplete="off"
        {...fieldProps}
        {...otherProps}
      />
    );
  }
}
