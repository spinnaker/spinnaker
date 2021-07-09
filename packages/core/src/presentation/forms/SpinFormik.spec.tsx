import React from 'react';
import { mount } from 'enzyme';
import { FormikProps } from 'formik';
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
