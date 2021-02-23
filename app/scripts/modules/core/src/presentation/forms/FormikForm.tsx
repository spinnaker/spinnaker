import { connect, FormikContext } from 'formik';
import React from 'react';

interface IFormikFormProps<T> {
  render: (props: FormikContext<T>) => React.ReactElement<any>;
}

interface IFormikFormImplProps<T> extends IFormikFormProps<T> {
  formik: FormikContext<T>;
}

/**
 * This component provides access to the current Formik `form` props.
 *
 * This can be useful to access current form data outside of a FormField or Input.
 *
 * This component adapts the formik `connect()` api as a render prop.
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
const FormikFormImpl = <T extends any>({ render, formik }: IFormikFormImplProps<T>) => render(formik);
export const FormikForm: React.ComponentType<IFormikFormProps<any>> = connect(FormikFormImpl);
