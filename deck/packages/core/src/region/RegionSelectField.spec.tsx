import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { RegionSelectField } from './RegionSelectField';

describe('RegionSelectField', () => {
  it('propagates a selection and renders the current component value', () => {
    const component = { region: 'us-east-1' };
    const onChange = vi.fn();
    const { rerender } = render(
      <RegionSelectField
        account="test-account"
        component={component}
        field="region"
        labelColumns={3}
        onChange={onChange}
        regions={[{ name: 'us-east-1' }, { name: 'us-west-2' }] as any}
      />,
    );

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'us-west-2' } });

    expect(component.region).toBe('us-west-2');
    expect(onChange).toHaveBeenCalledExactlyOnceWith('us-west-2');

    rerender(
      <RegionSelectField
        account="test-account"
        component={component}
        field="region"
        labelColumns={3}
        onChange={onChange}
        regions={[{ name: 'us-east-1' }, { name: 'us-west-2' }] as any}
      />,
    );
    expect(screen.getByRole('combobox')).toHaveValue('us-west-2');
  });
});
