import { render } from '@testing-library/react';
import React from 'react';

import type { VariableType } from './PipelineTemplateReader';
import { Variable } from './Variable';
import type { IVariableError } from './inputs/variableInput.service';

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
    it('renders a text-type input field for string type variables', () => {
      const { container } = render(<Variable {...generateProps('string', 'string')} />);
      expect(container.querySelectorAll('input[type="text"]')).toHaveLength(1);
    });

    it('renders a number-type input field for integer type variables', () => {
      const { container } = render(<Variable {...generateProps('int', 1)} />);
      expect(container.querySelectorAll('input[type="number"]')).toHaveLength(1);
    });

    it('renders a textarea field for object type variables', () => {
      const { container } = render(<Variable {...generateProps('object', 'yaml')} />);
      expect(container.querySelectorAll('textarea')).toHaveLength(1);
    });

    it('renders a set of text-type input fields for list type variables', () => {
      const { container } = render(<Variable {...generateProps('list', ['a', 'b', 'c'])} />);
      expect(container.querySelectorAll('input[type="text"]')).toHaveLength(3);
    });

    it('renders a checkbox for boolean type variables', () => {
      const { container } = render(<Variable {...generateProps('boolean', true)} />);
      expect(container.querySelectorAll('input[type="checkbox"]')).toHaveLength(1);
    });
  });
});
