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
    it('Renders a SpelToggle action button when appropriate based on context and props', () => {
      const contextAndPropConfigs: Array<ITestFormProps & { renderSpelToggle: number }> = [
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
          <TestForm contextSpelAware={config.contextSpelAware} propsSpelAware={config.propsSpelAware} />,
        );
        expect(component.find(SpelToggle).length).toEqual(config.renderSpelToggle);
      });
    });
    it('renders a freeform input by default if the field value is SpEL and freeform SpEL inputs are enabled', () => {
      const component = mount(<TestForm initialValue="${spel_account}" propsSpelAware={true} />);
      expect(component.find(TextAreaInput).length).toEqual(1);
      expect(component.find(ReactSelectInput).length).toEqual(0);
    });
    it('renders the default input if the field value is not SpEL', () => {
      const component = mount(<TestForm propsSpelAware={true} />);
      component.setProps({});
      expect(component.find(ReactSelectInput).length).toEqual(1);
      expect(component.find(TextAreaInput).length).toEqual(0);
    });
    it('clicking the SpelToggle switches the input type from default to freeform and clears the field value', () => {
      const component = mount(<TestForm propsSpelAware={true} />);
      expect(component.find(ReactSelectInput).length).toEqual(1);
      expect(component.find(TextAreaInput).length).toEqual(0);
      expect(component.find(ReactSelectInput).prop('value')).toEqual('my-account');
      component.find(SpelToggle).simulate('click');
      expect(component.find(ReactSelectInput).length).toEqual(0);
      expect(component.find(TextAreaInput).length).toEqual(1);
      expect(component.find(TextAreaInput).prop('value')).toEqual(null);
    });
    it('clicking the SpelToggle switches the input type from freeform to default and clears the field value', () => {
      const component = mount(<TestForm initialValue="${spel_account}" propsSpelAware={true} />);
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

interface ITestFormProps {
  contextSpelAware?: boolean;
  initialValue?: string;
  propsSpelAware?: boolean;
}

function TestForm(props: ITestFormProps) {
  return (
    <FormikSpelContextProvider value={props.contextSpelAware}>
      <SpinFormik
        initialValues={{
          account: props.initialValue || 'my-account',
        }}
        onSubmit={() => {}}
        render={() => (
          <FormikFormField
            fastField={false}
            name="account"
            label="Account"
            input={props => <ReactSelectInput {...props} />}
            spelAware={props.propsSpelAware}
          />
        )}
      />
    </FormikSpelContextProvider>
  );
}
