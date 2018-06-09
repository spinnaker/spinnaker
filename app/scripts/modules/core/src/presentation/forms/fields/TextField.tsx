import * as React from 'react';

import { BasicLayout } from '../layouts';
import { formikField, IFormikField } from './formikField';
import { PartialInputProps, IFieldLayoutProps, IFieldProps } from '../interface';

export type ITextFieldProps = IFieldProps & PartialInputProps;
export class TextField extends React.Component<ITextFieldProps> {
  static Formik: IFormikField<ITextFieldProps> = formikField<ITextFieldProps>(TextField);
  static defaultProps: Partial<IFieldProps> = { FieldLayout: BasicLayout };

  public render() {
    const { label, help, error, warning, preview, actions, ...rest } = this.props;
    const { FieldLayout, value, onChange, required, ...inputProps } = rest;

    const input = (
      <input value={value} onChange={evt => onChange(evt.target.value)} className="form-control" {...inputProps} />
    );

    const layoutProps: IFieldLayoutProps = { input, label, help, error, warning, preview, actions, required };
    return <FieldLayout {...layoutProps} />;
  }
}
