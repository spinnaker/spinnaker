import { mount } from 'enzyme';
import type { FormikProps } from 'formik';
import React from 'react';

import { SpinFormik } from './SpinFormik';

describe('SpinFormik', () => {
  it('touches all fields in initialValues', () => {
    let formik: FormikProps<any> = null;

    mount(
      <SpinFormik
        initialValues={{ foo: '123', bar: '456' }}
        onSubmit={() => null}
        render={(_formik) => {
          formik = _formik;
          return null;
        }}
      />,
    );

    expect(formik.touched).toEqual({ foo: true, bar: true });
  });
});
