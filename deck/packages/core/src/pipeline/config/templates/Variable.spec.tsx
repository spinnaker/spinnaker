import type { ReactWrapper } from 'enzyme';
import { mount } from 'enzyme';
import React from 'react';

import type { VariableType } from './PipelineTemplateReader';
import { Variable } from './Variable';
import type { IVariableError, IVariableProps } from './inputs/variableInput.service';

describe('Variable component', () => {
  const generateProps = (type: VariableType, value: any) => {
    return {
      variableMetadata: {
        type,
        name: 'variable',
      },
      variable: {
        name: 'variable',
        errors: [] as IVariableError[],
        value,
        type,
      },
      onChange: (): void => null,
    };
  };

  describe('input fields', () => {
    let component: ReactWrapper<IVariableProps, null>;
    beforeEach(() => {
      component = mount(<Variable {...generateProps('string', 'string')} />);
    });

    it('renders a text-type input field for string type variables', () => {
      component.setProps(generateProps('string', 'string'));
      expect(component.find('input[type="text"]').length).toEqual(1);
    });

    it('renders a number-type input field for integer type variables', () => {
      component.setProps(generateProps('int', 1));
      expect(component.find('input[type="number"]').length).toEqual(1);
    });

    it('renders a textarea field for object type variables', () => {
      component.setProps(generateProps('object', 'yaml'));
      expect(component.find('textarea').length).toEqual(1);
    });

    it('renders a set of text-type input fields for list type variables', () => {
      component.setProps(generateProps('list', ['a', 'b', 'c']));
      expect(component.find('input[type="text"]').length).toEqual(3);
    });

    it('renders a checkbox for boolean type variables', () => {
      component.setProps(generateProps('boolean', true));
      expect(component.find('input[type="checkbox"]').length).toEqual(1);
    });
  });
});
