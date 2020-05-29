import { FormikProps } from 'formik/dist/types';
import React from 'react';
import { mount } from 'enzyme';

import {
  FormikFormField,
  FormikSpelContextProvider,
  ReactSelectInput,
  SpelToggle,
  SpinFormik,
  TextAreaInput,
} from 'core/presentation';

describe('<FormikFormField/>', () => {
  describe('SpEL-awareness', () => {
    const AccountField = (props: { propsSpelAware?: boolean }) => (
      <FormikFormField
        name="account"
        label="Account"
        input={props => <ReactSelectInput {...props} />}
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
      contextAndPropConfigs.forEach(config => {
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
