import * as React from 'react';
import { Field, FieldProps, FormikProps } from 'formik';

interface IFormikFormProps<T> {
  render: (props: FormikProps<T>) => React.ReactNode;
}

/**
 * This component provides access to the current Formik `form` props.
 * This can be useful to access current form data outside of a FormField or Input.
 *
 * Example:
 * ```js
 * <FormikForm render={formik => {
 *   const username = formik.values.username;
 *   return (
 *     <h1>Hello {username}</h1>
 *   )
 * }} />
 * ```
 */
export class FormikForm<T = any> extends React.Component<IFormikFormProps<T>> {
  public render() {
    return <Field name="" render={(props: FieldProps<T>) => this.props.render(props.form)} />;
  }
}
