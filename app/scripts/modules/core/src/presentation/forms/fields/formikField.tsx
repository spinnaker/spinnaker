import * as React from 'react';
import { IFieldProps, IFormikFieldProps, Omit } from '../interface';

export type IFormikField<P extends IFieldProps<T>, T = string> = React.ComponentType<
  Omit<P, 'onChange' | 'value' | 'label'> & IFormikFieldProps
>;

export function formikField<P extends IFieldProps<T>, T = string>(
  FieldComponent: React.ComponentType<P>,
): IFormikField<P, T> {
  return (props: any) => {
    const { formik, name, ...rest } = props;

    return (
      <FieldComponent
        value={formik.values[name]}
        error={formik.errors[name]}
        onChange={(val: T) => formik.setFieldValue(name, val)}
        {...rest}
      />
    );
  };
}
