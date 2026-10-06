import { fireEvent, render, screen } from '@testing-library/react';
import { setupUser } from '../../../../../core/src/utils/testUtils/userEvent';
import type { Mock } from 'vitest';
import React from 'react';

import type { ILabelEditorProps } from './LabelEditor';
import LabelEditor from './LabelEditor';

describe('<LabelEditor />', () => {
  let onChangeSpy: Mock;
  let props: ILabelEditorProps;
  beforeEach(() => {
    onChangeSpy = vi.fn();
    props = {
      labelSelectors: [
        {
          key: 'my-label-1',
          kind: 'EQUALS',
          values: ['my-value-1', 'my-value-2'],
        },
        {
          key: 'my-label-2',
          kind: 'NOT_EQUALS',
          values: ['my-value-3'],
        },
      ],
      onLabelSelectorsChange: onChangeSpy,
    };
    render(<LabelEditor {...props} />);
  });

  describe('view', () => {
    it('renders a row for each label selector', () => {
      expect(screen.getAllByRole('row')).toHaveLength(props.labelSelectors.length + 2);
    });
    it('renders selector values as comma-separated lists', () => {
      const inputs = screen.getAllByRole('textbox');
      expect(inputs[1]).toHaveValue('my-value-1, my-value-2');
      expect(inputs[3]).toHaveValue('my-value-3');
    });
  });

  describe('functionality', () => {
    it('calls `props.onLabelSelectorsChange` when selector properties are changed', () => {
      const inputs = screen.getAllByRole('textbox');
      fireEvent.change(inputs[0], { target: { value: 'my-label-1-edited' } });
      expect(onChangeSpy).toHaveBeenCalledWith([
        {
          ...props.labelSelectors[0],
          key: 'my-label-1-edited',
        },
        props.labelSelectors[1],
      ]);
      fireEvent.change(inputs[3], { target: { value: 'my-value-3, my-value-4' } });
      expect(onChangeSpy).toHaveBeenCalledWith([
        props.labelSelectors[0],
        {
          ...props.labelSelectors[1],
          values: ['my-value-3', 'my-value-4'],
        },
      ]);
    });
    it('handles adding label selectors', async () => {
      const user = setupUser();
      await user.click(screen.getByRole('button', { name: 'Add Label' }));
      expect(onChangeSpy).toHaveBeenCalledWith([...props.labelSelectors, { key: '', kind: 'EQUALS', values: [] }]);
    });
    it('handles removing label selectors', async () => {
      const user = setupUser();
      await user.click(screen.getAllByRole('button', { name: 'Remove field' })[1]);
      expect(onChangeSpy).toHaveBeenCalledWith([
        {
          ...props.labelSelectors[0],
        },
      ]);
    });
  });
});
