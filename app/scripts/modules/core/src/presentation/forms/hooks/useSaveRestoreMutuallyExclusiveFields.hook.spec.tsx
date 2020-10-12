import { mount } from 'enzyme';
import { useSaveRestoreMutuallyExclusiveFields } from './useSaveRestoreMutuallyExclusiveFields.hook';
import { Formik, FormikProps } from 'formik';
import React from 'react';
import { FormikFormField } from '../fields';
import { SelectInput } from '../inputs';
import { SpinFormik } from '../SpinFormik';

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
  return mount(
    <SpinFormik
      ref={formikRef}
      onSubmit={null}
      initialValues={initialValues}
      render={(formik) => <OrderComponent formik={formik} />}
    />,
  );
};

describe('useSaveRestoreMutuallyExclusiveFields hook', () => {
  it(`clears out previously entered 'pizza' fields when the user chooses 'sandwich'`, () => {
    const formikRef = React.createRef<Formik>();
    const wrapper = setupTest(formikRef);

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});
    // cleared out the pizza field from the formik values
    expect(formikRef.current.getFormikBag().values).toEqual({ pizzaOrSandwich: 'sandwich' });
  });

  it(`clears out 'touched' status for 'pizza' fields when the user chooses 'sandwich'`, () => {
    const formikRef = React.createRef<Formik>();
    const wrapper = setupTest(formikRef);
    formikRef.current.setFieldTouched('topping', true);
    formikRef.current.setFieldTouched('crust', true);

    expect(formikRef.current.getFormikBag().touched.topping).toBe(true);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(true);

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});

    expect(formikRef.current.getFormikBag().touched.topping).toBe(null);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(null);
  });

  it(`restores previously saved 'pizza' fields when toggling back to 'pizza'`, () => {
    const formikRef = React.createRef<Formik>();
    const wrapper = setupTest(formikRef);

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});

    formikRef.current.setFieldValue('pizzaOrSandwich', 'pizza');
    wrapper.setProps({});

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
    const wrapper = setupTest(formikRef);
    formikRef.current.setFieldTouched('topping', true);
    formikRef.current.setFieldTouched('crust', true);

    expect(formikRef.current.getFormikBag().touched.topping).toBe(true);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(true);

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});
    formikRef.current.setFieldValue('pizzaOrSandwich', 'pizza');
    wrapper.setProps({});

    expect(formikRef.current.getFormikBag().touched.topping).toBe(true);
    expect(formikRef.current.getFormikBag().touched.crust).toBe(true);
  });

  it(`restores previously saved 'pizza' and 'sandwich' fields when toggling back and forth`, () => {
    const formikRef = React.createRef<Formik>();
    const wrapper = setupTest(formikRef);

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});

    formikRef.current.setFieldValue('bread', 'wheat');
    formikRef.current.setFieldValue('meat', 'ham');
    formikRef.current.setFieldValue('cheese', 'cheddar');
    wrapper.setProps({});

    formikRef.current.setFieldValue('pizzaOrSandwich', 'pizza');
    wrapper.setProps({});
    // restored the pizza fields
    expect(formikRef.current.getFormikBag().values).toEqual({
      pizzaOrSandwich: 'pizza',
      topping: 'pepperoni',
      crust: 'thin',
      sauce: 'red',
      cheese: 'cheddar',
    });

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});
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
    const wrapper = setupTest(formikRef);

    formikRef.current.setFieldValue('cheese', 'mozzarella');

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});

    formikRef.current.setFieldValue('bread', 'wheat');
    formikRef.current.setFieldValue('meat', 'ham');
    formikRef.current.setFieldValue('cheese', 'cheddar');

    formikRef.current.setFieldValue('pizzaOrSandwich', 'pizza');
    wrapper.setProps({});
    // restored the pizza fields
    expect(formikRef.current.getFormikBag().values.cheese).toEqual('mozzarella');

    formikRef.current.setFieldValue('pizzaOrSandwich', 'sandwich');
    wrapper.setProps({});
    // restored the sandwich fields
    expect(formikRef.current.getFormikBag().values.cheese).toEqual('cheddar');
  });
});
