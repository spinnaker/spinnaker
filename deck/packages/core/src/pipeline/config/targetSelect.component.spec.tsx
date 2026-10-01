import { render, screen } from '@testing-library/react';
import { setupUser } from '../../utils/testUtils/userEvent';
import React from 'react';

import * as TargetSelectExports from './TargetSelect';

describe('TargetSelect', () => {
  it('renders the native selector and updates the model target', async () => {
    const user = setupUser();
    const onChange = vi.fn();
    const model = { target: 'current_asg_dynamic' };
    render(<TargetSelectExports.TargetSelect model={model} onChange={onChange} options={targetOptions()} />);

    await user.click(screen.getByRole('combobox'));
    await user.click(screen.getByRole('button', { name: /Previous/ }));

    expect(model.target).toBe('ancestor_asg_dynamic');
    expect(onChange).toHaveBeenCalledWith('ancestor_asg_dynamic');
  });

  it('renders descriptions and filters options by search text', async () => {
    const user = setupUser();
    render(<TargetSelectExports.TargetSelect model={{ target: '' }} onChange={vi.fn()} options={targetOptions()} />);

    const input = screen.getByRole('combobox');
    await user.click(input);

    expect(screen.getByText('Previous server group')).toBeInTheDocument();

    await user.type(input, 'current');

    expect(screen.getAllByRole('button')).toHaveLength(1);
    expect(screen.getByRole('button', { name: /Current/ })).toHaveTextContent('Current server group');
  });

  it('supports clearing to None', async () => {
    const user = setupUser();
    const onChange = vi.fn();
    const model = { target: 'current_asg_dynamic' };
    render(<TargetSelectExports.TargetSelect model={model} onChange={onChange} options={targetOptions()} />);

    await user.click(screen.getByRole('button', { name: 'None' }));

    expect(model.target).toBe('');
    expect(onChange).toHaveBeenCalledWith('');
    expect(screen.getByRole('combobox')).toHaveAttribute('placeholder', 'None');
  });
});

function targetOptions() {
  return [
    { val: 'current_asg_dynamic', label: 'Current', description: 'Current server group' },
    { val: 'ancestor_asg_dynamic', label: 'Previous', description: 'Previous server group' },
  ] as any;
}
