import { act, render } from '@testing-library/react';
import type { Formik, FormikProps } from 'formik';
import React from 'react';

import { SpinFormik } from '../SpinFormik';
import { FormikFormField } from '../fields';
import { SelectInput } from '../inputs';
import { useSaveRestoreMutuallyExclusiveFields } from './useSaveRestoreMutuallyExclusiveFields.hook';

function PizzaComponent() {
  return (
    <>
      <FormikFormField
        name="topping"
        input={(props) => <SelectInput {...props} options={['peppers', 'mushrooms']} />}
      />
      <FormikFormField name="crust" input={(props) => <SelectInput {...props} options={['thin', 'deepdish']} />} />
      <FormikFormField name="sauce" input={(props) => <SelectInput {...props} options={['red', 'white']} />} />
      <FormikFormField
        name="cheese"
        input={(props) => <SelectInput {...props} options={['mozzarella', 'cheddar']} />}
      />
    </>
  );
}

function SandwichComponent() {
  return (
    <>
      <FormikFormField name="bread" input={(props) => <SelectInput {...props} options={['white', 'wheat']} />} />
      <FormikFormField name="meat" input={(props) => <SelectInput {...props} options={['ham', 'turkey']} />} />
      <FormikFormField name="cheese" input={(props) => <SelectInput {...props} options={['cheddar', 'swiss']} />} />
    </>
  );
}

function OrderComponent({ formik }: { formik: FormikProps<any> }) {
  useSaveRestoreMutuallyExclusiveFields(formik, formik.values.pizzaOrSandwich, {
    pizza: ['topping', 'crust', 'sauce', 'cheese'],
    sandwich: ['bread', 'meat', 'cheese'],
  });

  // Note: none of the FormikFormField components are necessary for these unit tests to work.
  // However, they provide clarity to the reader regarding what the hook intends to do.
  return (
    <>
      <FormikFormField
        name="pizzaOrSandwich"
        input={(props) => <SelectInput {...props} options={['pizza', 'sandwich']} />}
      />

      {formik.values.pizzaOrSandwich === 'pizza' && <PizzaComponent />}
      {formik.values.pizzaOrSandwich === 'sandwich' && <SandwichComponent />}
    </>
  );
}

const initialValues = {
  pizzaOrSandwich: 'pizza',
  topping: 'pepperoni',
  crust: 'thin',
  sauce: 'red',
  cheese: 'cheddar',
};

const setupTest = (formikRef: React.MutableRefObject<any>) => {
  return render(
    <SpinFormik
      ref={formikRef}
      onSubmit={null}
      initialValues={initialValues}
      render={(formik) => <OrderComponent formik={formik} />}
    />,
  );
};

function setFieldValue(formikRef: React.RefObject<Formik>, field: string, value: any) {
  act(() => formikRef.current.setFieldValue(field, value));
}

function setFieldTouched(formikRef: React.RefObject<Formik>, field: string, touched: boolean) {
  act(() => formikRef.current.setFieldTouched(field, touched));
}

describe('useSaveRestoreMutuallyExclusiveFields hook', () => {
  it(`clears out previously entered 'pizza' fields when the user chooses 'sandwich'`, () => {
    const formikRef = React.createRef<Formik>();
    setupTest(formikRef);

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');
    // cleared out the pizza field from the formik values
    expect(formikRef.current.getFormikBag().values).toEqual({ pizzaOrSandwich: 'sandwich' });
  });

  it(`clears out 'touched' status for 'pizza' fields when the user chooses 'sandwich'`, () => {
    const formikRef = React.createRef<Formik>();
    setupTest(formikRef);
    setFieldTouched(formikRef, 'topping', true);
    setFieldTouched(formikRef, 'crust', true);

    expect(formikRef.current.getFormikBag().touched.topping).toBe(true);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(true);

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');

    expect(formikRef.current.getFormikBag().touched.topping).toBe(null);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(null);
  });

  it(`restores previously saved 'pizza' fields when toggling back to 'pizza'`, () => {
    const formikRef = React.createRef<Formik>();
    setupTest(formikRef);

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');

    setFieldValue(formikRef, 'pizzaOrSandwich', 'pizza');

    // restored the pizza fields
    expect(formikRef.current.getFormikBag().values).toEqual({
      pizzaOrSandwich: 'pizza',
      topping: 'pepperoni',
      crust: 'thin',
      sauce: 'red',
      cheese: 'cheddar',
    });
  });

  it(`restores previously saved touched statuses for 'pizza' fields when toggling back to 'pizza'`, () => {
    const formikRef = React.createRef<Formik>();
    setupTest(formikRef);
    setFieldTouched(formikRef, 'topping', true);
    setFieldTouched(formikRef, 'crust', true);

    expect(formikRef.current.getFormikBag().touched.topping).toBe(true);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(true);

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');
    setFieldValue(formikRef, 'pizzaOrSandwich', 'pizza');

    expect(formikRef.current.getFormikBag().touched.topping).toBe(true);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(true);
  });

  it(`restores previously saved 'pizza' and 'sandwich' fields when toggling back and forth`, () => {
    const formikRef = React.createRef<Formik>();
    setupTest(formikRef);

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');

    setFieldValue(formikRef, 'bread', 'wheat');
    setFieldValue(formikRef, 'meat', 'ham');
    setFieldValue(formikRef, 'cheese', 'cheddar');

    setFieldValue(formikRef, 'pizzaOrSandwich', 'pizza');
    // restored the pizza fields
    expect(formikRef.current.getFormikBag().values).toEqual({
      pizzaOrSandwich: 'pizza',
      topping: 'pepperoni',
      crust: 'thin',
      sauce: 'red',
      cheese: 'cheddar',
    });

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');
    // restored the sandwich fields
    expect(formikRef.current.getFormikBag().values).toEqual({
      pizzaOrSandwich: 'sandwich',
      bread: 'wheat',
      meat: 'ham',
      cheese: 'cheddar',
    });
  });

  it(`saves and restores different values for keys that exist in multiple field sets`, () => {
    const formikRef = React.createRef<Formik>();
    setupTest(formikRef);

    setFieldValue(formikRef, 'cheese', 'mozzarella');

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');

    setFieldValue(formikRef, 'bread', 'wheat');
    setFieldValue(formikRef, 'meat', 'ham');
    setFieldValue(formikRef, 'cheese', 'cheddar');

    setFieldValue(formikRef, 'pizzaOrSandwich', 'pizza');
    // restored the pizza fields
    expect(formikRef.current.getFormikBag().values.cheese).toEqual('mozzarella');

    setFieldValue(formikRef, 'pizzaOrSandwich', 'sandwich');
    // restored the sandwich fields
    expect(formikRef.current.getFormikBag().values.cheese).toEqual('cheddar');
  });
});
