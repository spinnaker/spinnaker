import * as React from 'react';
import { FormikProps } from 'formik';
import { IFieldProps, IFormikFieldProps, Omit } from '../interface';

export type IFormikField<P extends IFieldProps<T>, T = string> = React.ComponentType<
  Omit<P, 'onChange' | 'value' | 'label'> & IFormikFieldProps
>;

export function formikField<P extends IFieldProps<T>, T = string>(
  FieldComponent: React.ComponentType<P>,
): IFormikField<P, T> {
  return (props: any) => {
    const { formik, name, ...rest } = props;
    const formikProps: FormikProps<any> = formik;

    return (
      <FieldComponent
        value={formikProps.values[name]}
        error={formikProps.errors[name]}
        touched={formikProps.touched[name]}
        onChange={(val: T) => formikProps.setFieldValue(name, val)}
        onBlur={() => formikProps.setFieldTouched(name, true)}
        {...rest}
      />
    );
  };
}
