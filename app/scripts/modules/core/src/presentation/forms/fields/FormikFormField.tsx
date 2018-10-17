import * as React from 'react';
import { Field, FieldProps, getIn } from 'formik';
import { isUndefined } from 'lodash';

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
    const { touched, validationMessage, validationStatus } = this.props; // IValidationProps

    return (
      <Field
        name={name}
        validate={validate}
        render={(props: FieldProps<any>) => {
          const { field, form } = props;

          const formikError = getIn(form.errors, name);
          const message = !isUndefined(validationMessage) ? validationMessage : formikError;
          const status = !isUndefined(validationStatus) ? validationStatus : formikError ? 'error' : null;
          const isTouched = !isUndefined(touched) ? touched : getIn(form.touched, name);

          const validationProps: IValidationProps = {
            validationMessage: message,
            validationStatus: status,
            touched: isTouched,
          };

          const inputElement = renderContent(input, { field, validation: validationProps });
          return renderContent(layout, { ...fieldLayoutPropsWithoutInput, ...validationProps, input: inputElement });
        }}
      />
    );
  }
}
