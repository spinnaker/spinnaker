import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { CopyToClipboard } from './CopyToClipboard';
import { logger } from '../Logger';

describe('<CopyToClipboard />', () => {
  beforeEach(() => {
    vi.spyOn(logger, 'log').mockReturnValue(undefined);
    document.execCommand = vi.fn().mockReturnValue(true);
  });

  it('renders a textarea with the text value', () => {
    render(<CopyToClipboard toolTip="Copy Rebel Girl" text="Rebel Girl" />);

    expect(screen.getByDisplayValue('Rebel Girl')).toBeInTheDocument();
  });

  it('shows the configured tooltip on mouseover', async () => {
    render(<CopyToClipboard toolTip="Copy Rebel Girl" text="Rebel Girl" />);

    fireEvent.mouseOver(screen.getByRole('button', { name: 'Copy to clipboard' }));

    expect(await screen.findByText('Copy Rebel Girl')).toBeInTheDocument();
  });

  it('shows success feedback when clicked without a default tooltip', async () => {
    render(<CopyToClipboard text="No Tooltip" />);
    const button = screen.getByRole('button', { name: 'Copy to clipboard' });

    fireEvent.mouseOver(button);
    expect(screen.queryByText('Copied!')).not.toBeInTheDocument();
    await userEvent.click(button);

    expect(await screen.findByText('Copied!')).toBeInTheDocument();
    expect(document.execCommand).toHaveBeenCalledWith('copy');
  });

  it('fires a GA event on click', async () => {
    render(<CopyToClipboard analyticsLabel="rebel-girl-label" toolTip="Copy Rebel Girl" text="Rebel Girl" />);

    await userEvent.click(screen.getByRole('button', { name: 'Copy to clipboard' }));

    expect(logger.log).toHaveBeenCalledTimes(1);
    expect(logger.log).toHaveBeenCalledWith({
      category: 'Copy to Clipboard',
      action: 'copy',
      data: { label: 'rebel-girl-label' },
    });
  });
});
