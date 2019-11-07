import * as React from 'react';

import { set } from 'lodash';
import { Formik, FormikConfig } from 'formik';

import { traverseObject } from 'core/utils';

/**
 * This component wraps the <Formik/> component, applying fixes and spinnaker opinions
 * Use this component like you would use the <Formik/> component
 */
function SpinFormikImpl<Values extends {}>(props: FormikConfig<Values>, ref?: React.MutableRefObject<Formik<Values>>) {
  const formikRef = ref || React.useRef<Formik<Values>>();
  const formik = formikRef.current;
  const defaultIsInitialValid = () => formikRef.current && Object.keys(formikRef.current.state.errors).length === 0;

  // When a form is reloaded with existing data, we usually want to show validation errors immediately.
  // When the form is first rendered, mark all fields in initialValues as "touched".
  // Then run initial validation.
  React.useEffect(() => {
    if (formik) {
      const initialTouched = {};
      traverseObject(props.initialValues, (path: string) => set(initialTouched, path, true));
      formik.setTouched(initialTouched);
      formik.getFormikActions().validateForm();
    }
  }, [!!formik]);

  return <Formik<Values> ref={formikRef} isInitialValid={props.isInitialValid || defaultIsInitialValid} {...props} />;
}

export const SpinFormik = (React.forwardRef(SpinFormikImpl) as any) as typeof Formik;
