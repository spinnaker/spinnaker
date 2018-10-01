import * as React from 'react';
import * as classNames from 'classnames';

import { BasicLayout } from '../layouts';
import { formikField, IFormikField } from './formikField';
import { PartialInputProps, IFieldLayoutProps, IFieldProps } from '../interface';

export type ITextFieldProps = IFieldProps & PartialInputProps;
export class TextField extends React.Component<ITextFieldProps> {
  public static Formik: IFormikField<ITextFieldProps> = formikField<ITextFieldProps>(TextField);
  public static defaultProps: Partial<IFieldProps> = { FieldLayout: BasicLayout };

  public render() {
    const { label, help, touched, error, warning, preview, actions, ...rest } = this.props;
    const { FieldLayout, value, onChange, onBlur, required, ...inputProps } = rest;

    const className = classNames({
      'form-control': true,
      'ng-dirty': !!touched,
      'ng-invalid': !!error,
    });

    const input = (
      <input
        value={value}
        onChange={evt => onChange && onChange(evt.target.value)}
        onBlur={evt => onBlur && onBlur(evt)}
        className={className}
        {...inputProps}
      />
    );

    const layoutProps: IFieldLayoutProps = { input, label, help, error, warning, preview, actions, touched, required };
    return <FieldLayout {...layoutProps} />;
  }
}
