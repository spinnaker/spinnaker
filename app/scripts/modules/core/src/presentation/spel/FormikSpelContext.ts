import * as React from 'react';

/**
 * The only Formik SpEL-aware mode that has been implemented so far allows users
 * to toggle between the default input and a freeform text input.
 * In the future, we will implement a mode that allows users to toggle between
 * the default input and a (to-be-created) VariablePicker component hydrated by
 * upstream Evaluate Variables stages.
 */
export const FormikSpelContext = React.createContext<boolean>(false);

export const { Provider: FormikSpelContextProvider } = FormikSpelContext;
