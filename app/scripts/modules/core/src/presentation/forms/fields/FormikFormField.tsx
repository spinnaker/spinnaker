import * as React from 'react';
import { Field, FieldProps } from 'formik';
import { get, isUndefined } from 'lodash';

import { ICommonFormFieldProps, IFieldLayoutPropsWithoutInput, IValidationProps } from '../interface';
import { StandardFieldLayout } from '../layouts';
import { renderContent } from './renderContent';

export interface IFormikFieldProps {
  name: string;
  validate?: (value: any) => string | Function | Promise<any>;
}

export type IFormikFormFieldProps = IFormikFieldProps & ICommonFormFieldProps & IFieldLayoutPropsWithoutInput;

export class FormikFormField extends React.Component<IFormikFormFieldProps> {
  public static defaultProps: Partial<IFormikFormFieldProps> = {
    layout: StandardFieldLayout,
  };

  public render() {
    const { input, layout, name, validate } = this.props; // ICommonFieldProps & name & validate
    const { label, help, required, actions } = this.props; // IFieldLayoutPropsWithoutInput
    const fieldLayoutPropsWithoutInput: IFieldLayoutPropsWithoutInput = { label, help, required, actions };
    const { touched, error } = this.props; // IValidationProps

    return (
      <Field
        name={name}
        validate={validate}
        render={(props: FieldProps<any>) => {
          const { field, form } = props;

          const validationProps: IValidationProps = {
            error: !isUndefined(error) ? error : get(form.errors, name),
            touched: !isUndefined(touched) ? touched : get(form.touched, name),
            preview: null,
            warning: null,
          };

          const inputElement = renderContent(input, { field, validation: validationProps });
          return renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement });
        }}
      />
    );
  }
}
