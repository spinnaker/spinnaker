import * as React from 'react';
import { Field, FieldProps, FormikProps } from 'formik';

interface ICurrentFormProps<T> {
  render: (props: FormikProps<T>) => React.ReactNode;
}

/**
 * This component provides access to the current Formik `form` props.
 * This can be useful to access current form data outside of a FormField or Input.
 *
 * Example:
 * ```js
 * <CurrentForm render={formik => {
 *   const username = formik.values.username;
 *   return (
 *     <h1>Hello {username}</h1>
 *   )
 * }} />
 * ```
 */
export class CurrentForm<T = any> extends React.Component<ICurrentFormProps<T>> {
  public render() {
    return <Field name="" render={(props: FieldProps<T>) => this.props.render(props.form)} />;
  }
}
