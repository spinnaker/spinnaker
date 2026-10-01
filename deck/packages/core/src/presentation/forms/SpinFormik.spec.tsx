import { render, screen } from '@testing-library/react';
import type { FormikProps } from 'formik';
import React from 'react';

import { SpinFormik } from './SpinFormik';

describe('SpinFormik', () => {
  it('touches all fields in initialValues', async () => {
    render(
      <SpinFormik
        initialValues={{ foo: '123', bar: '456' }}
        onSubmit={() => null}
        render={(formik: FormikProps<any>) => <output>{JSON.stringify(formik.touched)}</output>}
      />,
    );

    expect(await screen.findByText('{"foo":true,"bar":true}')).toBeInTheDocument();
  });
});
