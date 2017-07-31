import { mock } from 'angular';
import * as React from 'react';
import { mount, ReactWrapper } from 'enzyme';

import { REACT_MODULE } from 'core/reactShims/react.module';
import { HELP_CONTENTS } from 'core/help/help.contents';
import { HELP_CONTENTS_REGISTRY } from 'core/help/helpContents.registry';
import { PIPELINE_TEMPLATE_MODULE } from './pipelineTemplate.module';
import { Variable } from './Variable';
import { IVariableError, IVariableProps } from './inputs/variableInput.service';
import { VariableType } from './pipelineTemplate.service';

describe('Variable component', () => {
  const generateProps = (type: VariableType, value: any) => {
    return {
      variableMetadata: {
        type,
        name: 'variable'
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

  beforeEach(
    mock.module(
      PIPELINE_TEMPLATE_MODULE,
      HELP_CONTENTS_REGISTRY,
      HELP_CONTENTS,
      REACT_MODULE
    )
  );

  beforeEach(mock.inject(() => {})); // Angular is lazy.

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
