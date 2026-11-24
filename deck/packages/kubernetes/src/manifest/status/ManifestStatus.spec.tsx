import { render, screen } from '@testing-library/react';
import React from 'react';

import { ManifestStatus } from './ManifestStatus';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';

describe('<ManifestStatus />', () => {
  const status = (overrides: { [key: string]: { state: boolean; message?: string } }) => ({
    available: { state: true },
    stable: { state: true },
    paused: { state: false },
    failed: { state: false },
    ...overrides,
  });

  it('renders status messages as plain text rather than Markdown', async () => {
    const user = setupUser();
    const message = 'Degraded: [see details](https://example.com) ![x](https://example.com/x.png) <b>bold</b>';
    const { container } = render(<ManifestStatus status={status({ stable: { state: false, message } }) as any} />);

    const band = screen.getByText('Transitioning');
    expect(band).toHaveClass('band-active');
    expect(container.querySelectorAll('.band')).toHaveLength(1);

    await user.hover(band);

    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip.textContent).toBe(message);
    expect(tooltip.querySelector('a')).toBeNull();
    expect(tooltip.querySelector('img')).toBeNull();
    expect(tooltip.querySelector('b')).toBeNull();
  });

  it('shows no tooltip content when there is no message', async () => {
    const user = setupUser();
    render(<ManifestStatus status={status({ available: { state: false } }) as any} />);

    const band = screen.getByText('Not Fully Available');
    expect(band).toHaveClass('band-warning');

    await user.hover(band);

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('shows the paused band with its message', async () => {
    const user = setupUser();
    render(<ManifestStatus status={status({ paused: { state: true, message: 'Paused by user' } }) as any} />);

    const band = screen.getByText('Rollout Paused');
    expect(band).toHaveClass('band-info');

    await user.hover(band);

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Paused by user');
  });
});
