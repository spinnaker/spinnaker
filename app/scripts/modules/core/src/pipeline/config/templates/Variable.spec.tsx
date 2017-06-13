import { mock } from 'angular';
import * as React from 'react';
import { mount } from 'enzyme';

import { HELP_CONTENTS, HELP_CONTENTS_REGISTRY, REACT_MODULE } from '@spinnaker/core';

import { PIPELINE_TEMPLATE_MODULE } from './pipelineTemplate.module';
import { Variable } from './Variable';
import { IVariableError } from './inputs/variableInput.service';
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
    it('renders an input field to match the variable type', () => {
      const component = mount(<Variable {...generateProps('string', 'string')} />);

      component.setProps(generateProps('string', 'string'));
      expect(component.find('input[type="text"]').length).toEqual(1);

      component.setProps(generateProps('int', 1));
      expect(component.find('input[type="number"]').length).toEqual(1);

      component.setProps(generateProps('object', 'yaml'));
      expect(component.find('textarea').length).toEqual(1);

      component.setProps(generateProps('list', ['a', 'b', 'c']));
      expect(component.find('input[type="text"]').length).toEqual(3);
    });
  });
});
