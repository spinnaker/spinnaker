import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { ScopeClusterSelector } from './ScopeClusterSelector';

describe('<ScopeClusterSelector />', () => {
  it('uses link-styled buttons to toggle between dropdown and free-form modes', async () => {
    render(<ScopeClusterSelector clusters={['api']} model="" onChange={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Toggle for text input' }));
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.getByRole('textbox')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Toggle for list of existing clusters' }));
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
});
