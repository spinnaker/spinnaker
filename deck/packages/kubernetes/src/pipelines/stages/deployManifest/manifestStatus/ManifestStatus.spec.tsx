import { screen, within } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import { ManifestStatus } from './ManifestStatus';
import { renderWithRouter } from '../../../../../../core/src/utils/testUtils/rtl';
import { setupUser } from '../../../../../../core/src/utils/testUtils/userEvent';

describe('<ManifestStatus />', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(new Promise(() => {}) as any);
  });

  const heading = (container: HTMLElement) => container.querySelector('dl.manifest-status dt');

  it('should render the manifest kind and name in the heading', () => {
    const manifest = {
      manifest: {
        kind: 'ConfigMap',
        metadata: {
          name: 'my-configmap',
        },
      },
    };

    const { container } = renderWithRouter(<ManifestStatus manifest={manifest as any} account="my-account" />);

    expect(heading(container).textContent).toBe('ConfigMap my-configmap');
  });

  it('should distinguish between multiple manifests of the same kind', () => {
    const first = {
      manifest: {
        kind: 'ConfigMap',
        metadata: {
          name: 'first-configmap',
        },
      },
    };
    const second = {
      manifest: {
        kind: 'ConfigMap',
        metadata: {
          name: 'second-configmap',
        },
      },
    };

    const { container: firstContainer } = renderWithRouter(
      <ManifestStatus manifest={first as any} account="my-account" />,
    );
    const { container: secondContainer } = renderWithRouter(
      <ManifestStatus manifest={second as any} account="my-account" />,
    );

    expect(heading(firstContainer).textContent).toBe('ConfigMap first-configmap');
    expect(heading(secondContainer).textContent).toBe('ConfigMap second-configmap');
    expect(heading(firstContainer).textContent).not.toBe(heading(secondContainer).textContent);
  });

  it('exposes a copy-to-clipboard button for the manifest name', async () => {
    const user = setupUser();
    const manifest = {
      manifest: {
        kind: 'ConfigMap',
        metadata: {
          name: 'my-configmap',
        },
      },
    };

    const { container } = renderWithRouter(<ManifestStatus manifest={manifest as any} account="my-account" />);

    const status = within(container.querySelector('dl.manifest-status') as HTMLElement);
    const copyButtons = status.getAllByRole('button', { name: 'Copy to clipboard' });
    expect(copyButtons).toHaveLength(1);
    expect(copyButtons[0]).toHaveClass('copy-to-clipboard');
    expect(status.getByRole('textbox')).toHaveValue('my-configmap');

    await user.hover(copyButtons[0]);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Copy my-configmap');
  });
});
