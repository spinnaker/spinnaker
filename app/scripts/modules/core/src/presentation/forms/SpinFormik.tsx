import * as React from 'react';
import { Formik, FormikConfig } from 'formik';

/**
 * This component wraps the <Formik/> component, applying fixes and spinnaker opinions
 * Use this component like you would use the <Formik/> component
 */
function SpinFormikImpl<Values extends {}>(props: FormikConfig<Values>, ref?: React.MutableRefObject<Formik<Values>>) {
  const formikRef = ref || React.useRef<Formik<Values>>();
  const defaultIsInitialValid = () => formikRef.current && Object.keys(formikRef.current.state.errors).length === 0;

  // Run initial validation when the form is first rendered
  React.useEffect(() => {
    formikRef.current && formikRef.current.getFormikActions().validateForm();
  }, [formikRef.current]);

  return <Formik<Values> ref={formikRef} isInitialValid={props.isInitialValid || defaultIsInitialValid} {...props} />;
}

export const SpinFormik = (React.forwardRef(SpinFormikImpl) as any) as typeof Formik;
