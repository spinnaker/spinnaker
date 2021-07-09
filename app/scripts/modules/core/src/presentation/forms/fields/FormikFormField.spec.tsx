import { FormikProps } from 'formik/dist/types';
import React from 'react';
import { mount } from 'enzyme';

import {
  FormikFormField,
  FormikSpelContextProvider,
  IFormInputProps,
  ReactSelectInput,
  SpelToggle,
  SpinFormik,
  TextAreaInput,
  TextInput,
} from '../..';

const makeSpy = () => {
  const renderSpy = jasmine.createSpy('render');
  const inputProps = () => renderSpy.calls.mostRecent().args[0] as IFormInputProps;
  const InputSpy = (props: any) => {
    renderSpy(props);
    return <TextInput {...props} />;
  };
  return { inputProps, renderSpy, InputSpy };
};

const asyncTick = () => new Promise((resolve) => setTimeout(resolve));

describe('<FormikFormField/>', () => {
  it(`renders the input`, () => {
    const { renderSpy, InputSpy } = makeSpy();
    mount(<Test initialValues={{}} render={() => <FormikFormField name="name" input={InputSpy} />} />);
    expect(renderSpy).toHaveBeenCalled();
  });

  it(`passes the field name to the input`, () => {
    const { inputProps, InputSpy } = makeSpy();
    mount(<Test initialValues={{}} render={() => <FormikFormField name="foo.bar" input={InputSpy} />} />);
    expect(inputProps().name).toBe('foo.bar');
  });

  it(`passes the field value to the input using the 'name' as an identifier`, () => {
    const { inputProps, InputSpy } = makeSpy();
    const initialValues = { foo: { bar: 'abc123' } };
    mount(<Test initialValues={initialValues} render={() => <FormikFormField name="foo.bar" input={InputSpy} />} />);
    expect(inputProps().value).toBe('abc123');
  });

  it(`passes validation information to the input`, async () => {
    const { inputProps, InputSpy } = makeSpy();
    const initialValues = { foo: { bar: 'abc123' } };
    const validate = () => ({ foo: { bar: 'bad' } });
    mount(
      <Test
        validate={validate}
        initialValues={initialValues}
        render={() => <FormikFormField name="foo.bar" input={InputSpy} />}
      />,
    );
    await asyncTick();
    expect(inputProps().validation.messageNode).toBe('bad');
    expect(inputProps().validation.category).toBe('error');
  });

  it(`does not index into strings for errors`, async () => {
    const { inputProps, InputSpy } = makeSpy();
    const initialValues = { foo: { bar: ['abc'] } };
    const validate = () => ({ foo: { bar: 'bad' } });
    mount(
      <Test
        validate={validate}
        initialValues={initialValues}
        render={() => <FormikFormField name="foo.bar[0]" input={InputSpy} />}
      />,
    );
    await asyncTick();
    expect(inputProps().value).toBe('abc');
    // in errors, foo.bar[0] error should be null, not 'b'
    expect(inputProps().validation.messageNode).toBe(null);
    expect(inputProps().validation.category).toBe(null);
  });

  describe('SpEL-awareness', () => {
    const AccountField = (props: { propsSpelAware?: boolean }) => (
      <FormikFormField
        name="account"
        label="Account"
        input={(props) => <ReactSelectInput {...props} />}
        spelAware={props.propsSpelAware}
      />
    );

    it('Renders a SpelToggle action button when appropriate based on context and props', () => {
      const contextAndPropConfigs: Array<Partial<ITestFormWrapperProps> & { renderSpelToggle: number }> = [
        {
          propsSpelAware: false,
          renderSpelToggle: 0,
        },
        {
          contextSpelAware: false,
          renderSpelToggle: 0,
        },
        {
          contextSpelAware: true,
          propsSpelAware: false,
          renderSpelToggle: 0,
        },
        {
          propsSpelAware: true,
          renderSpelToggle: 1,
        },
        {
          contextSpelAware: true,
          renderSpelToggle: 1,
        },
        {
          contextSpelAware: false,
          propsSpelAware: true,
          renderSpelToggle: 1,
        },
      ];
      contextAndPropConfigs.forEach((config) => {
        const component = mount(
          <Test
            contextSpelAware={config.contextSpelAware}
            propsSpelAware={config.propsSpelAware}
            render={() => <AccountField propsSpelAware={config.propsSpelAware} />}
          />,
        );
        expect(component.find(SpelToggle).length).toEqual(config.renderSpelToggle);
      });
    });

    it('renders a freeform input by default if the field value is SpEL and freeform SpEL inputs are enabled', () => {
      const component = mount(
        <Test initialValues={{ account: '${spel_account}' }} render={() => <AccountField propsSpelAware={true} />} />,
      );
      expect(component.find(TextAreaInput).length).toEqual(1);
      expect(component.find(ReactSelectInput).length).toEqual(0);
    });

    it('does not render the input even once if the field value is SpEL and freeform SpEL inputs are enabled', () => {
      const spy = jasmine.createSpy();
      const NeverRenderedComponent = () => {
        spy();
        return <span />;
      };
      const TestField = () => (
        <FormikFormField name="account" label="Account" input={NeverRenderedComponent} spelAware={true} />
      );
      mount(<Test initialValues={{ account: '${spel_account}' }} render={() => <TestField />} />);
      expect(spy).not.toHaveBeenCalled();
    });

    it('renders the default input if the field value is not SpEL', () => {
      const component = mount(
        <Test initialValues={{ account: 'account' }} render={() => <AccountField propsSpelAware={true} />} />,
      );
      component.setProps({});
      expect(component.find(ReactSelectInput).length).toEqual(1);
      expect(component.find(TextAreaInput).length).toEqual(0);
    });

    it('clicking the SpelToggle switches the input type from default to freeform and clears the field value', () => {
      const component = mount(
        <Test initialValues={{ account: 'my-account' }} render={() => <AccountField propsSpelAware={true} />} />,
      );
      expect(component.find(ReactSelectInput).length).toEqual(1);
      expect(component.find(TextAreaInput).length).toEqual(0);
      expect(component.find(ReactSelectInput).prop('value')).toEqual('my-account');
      component.find(SpelToggle).simulate('click');
      expect(component.find(ReactSelectInput).length).toEqual(0);
      expect(component.find(TextAreaInput).length).toEqual(1);
      expect(component.find(TextAreaInput).prop('value')).toEqual(null);
    });

    it('clicking the SpelToggle switches the input type from freeform to default and clears the field value', () => {
      const component = mount(
        <Test initialValues={{ account: '${spel_account}' }} render={() => <AccountField propsSpelAware={true} />} />,
      );
      expect(component.find(ReactSelectInput).length).toEqual(0);
      expect(component.find(TextAreaInput).length).toEqual(1);
      expect(component.find(TextAreaInput).prop('value')).toEqual('${spel_account}');
      component.find(SpelToggle).simulate('click');
      expect(component.find(ReactSelectInput).length).toEqual(1);
      expect(component.find(TextAreaInput).length).toEqual(0);
      expect(component.find(ReactSelectInput).prop('value')).toEqual(null);
    });
  });
});

interface ITestFormWrapperProps {
  render: (props: FormikProps<any>) => React.ReactNode;
  validate?: (form: any) => any;
  initialValues?: any;
  contextSpelAware?: boolean;
  propsSpelAware?: boolean;
}

function Test(props: ITestFormWrapperProps) {
  return (
    <FormikSpelContextProvider value={props.contextSpelAware}>
      <SpinFormik
        initialValues={props.initialValues || {}}
        validate={props.validate || (() => ({}))}
        onSubmit={() => {}}
        render={props.render}
      />
    </FormikSpelContextProvider>
  );
}
