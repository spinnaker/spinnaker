import { render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import { JobManifestPodLogs } from './JobManifestPodLogs';
import type { IPodNameProvider } from '../PodNameProvider';
import { SETTINGS } from '../../config/settings';
import { InstanceReader } from '../../instance/InstanceReader';
import { setupUser } from '../../utils/testUtils';

const makeProvider = (name: string): IPodNameProvider => ({ getPodName: () => name });

const mockConsoleOutput = {
  output: [
    { name: 'main', output: 'log line 1' },
    { name: 'sidecar', output: 'log line 2' },
  ],
};

const defaultProps = {
  account: 'test-account',
  location: 'test-namespace',
  linkName: 'Console Output',
  podNamesProviders: [makeProvider('test-pod')],
};

const footerButton = (name: string) =>
  within(document.querySelector('.modal-footer') as HTMLElement).getByRole('button', { name });

const intervalIdFor = (setIntervalSpy: Mock, delay: number) => {
  const call = setIntervalSpy.mock.calls.findIndex(([, ms]) => ms === delay);
  expect(call).toBeGreaterThanOrEqual(0);
  return setIntervalSpy.mock.results[call].value;
};

describe('JobManifestPodLogs', () => {
  let getConsoleOutputSpy: Mock;

  beforeEach(() => {
    getConsoleOutputSpy = vi
      .spyOn(InstanceReader, 'getConsoleOutput')
      .mockReturnValue(Promise.resolve(mockConsoleOutput));
    SETTINGS.consoleLogRefreshIntervalMs = 30000;
  });

  afterEach(() => {
    SETTINGS.resetToOriginal();
  });

  const renderAndOpenLogs = async (user = setupUser()) => {
    const rendered = render(<JobManifestPodLogs {...defaultProps} />);
    await user.click(screen.getByText('Console Output', { selector: 'a.clickable' }));
    await screen.findByText('main-1');
    return { ...rendered, user };
  };

  describe('rendering', () => {
    it('renders the link when all pod names are available', () => {
      render(<JobManifestPodLogs {...defaultProps} />);
      expect(screen.getByText('Console Output', { selector: 'a.clickable' })).toBeInTheDocument();
    });

    it('renders nothing when a pod name is empty', () => {
      const { container } = render(<JobManifestPodLogs {...defaultProps} podNamesProviders={[makeProvider('')]} />);
      expect(container).toBeEmptyDOMElement();
    });
  });

  describe('manual refresh', () => {
    it('fetches logs when the link is clicked', async () => {
      const user = setupUser();
      render(<JobManifestPodLogs {...defaultProps} />);
      await user.click(screen.getByText('Console Output', { selector: 'a.clickable' }));
      expect(getConsoleOutputSpy).toHaveBeenCalledWith('test-account', 'test-namespace', 'pod test-pod', 'kubernetes');
      await screen.findByText('main-1');
    });

    it('renders a tab per container log after logs load', async () => {
      await renderAndOpenLogs();

      const tabs = Array.from(document.querySelectorAll('.console-output-tab'));
      expect(tabs.map((tab) => tab.textContent)).toEqual(['main-1', 'sidecar-2']);
      expect(screen.getByText('main-1')).toHaveClass('selected');
      expect(screen.getByText('log line 1')).toBeInTheDocument();
    });

    it('re-fetches logs when Refresh is clicked', async () => {
      const { user } = await renderAndOpenLogs();

      getConsoleOutputSpy.mockClear();
      await user.click(footerButton('Refresh'));
      expect(getConsoleOutputSpy).toHaveBeenCalledTimes(1);
      await screen.findByText('main-1');
    });

    it('preserves the selected tab across refreshes', async () => {
      const { user } = await renderAndOpenLogs();

      await user.click(screen.getByText('sidecar-2'));
      expect(screen.getByText('sidecar-2')).toHaveClass('selected');
      expect(screen.getByText('log line 2')).toBeInTheDocument();

      await user.click(footerButton('Refresh'));
      await waitFor(() => expect(getConsoleOutputSpy).toHaveBeenCalledTimes(2));

      expect(await screen.findByText('sidecar-2')).toHaveClass('selected');
      expect(screen.getByText('main-1')).not.toHaveClass('selected');
      expect(screen.getByText('log line 2')).toBeInTheDocument();
    });
  });

  describe('auto-refresh toggle', () => {
    it('switches auto-refresh on when the toggle is clicked', async () => {
      const { user } = await renderAndOpenLogs();

      await user.click(footerButton('Auto-Refresh: Off'));

      expect(footerButton('Auto-Refresh: On')).toHaveClass('btn-primary');
    });

    it('registers a setInterval with the configured refresh interval when auto-refresh is enabled', async () => {
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      const { user } = await renderAndOpenLogs();

      await user.click(footerButton('Auto-Refresh: Off'));

      expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 30000);
    });

    it('clears interval when auto-refresh is toggled off', async () => {
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');
      const { user } = await renderAndOpenLogs();

      await user.click(footerButton('Auto-Refresh: Off'));
      const intervalId = intervalIdFor(setIntervalSpy, 30000);
      await user.click(footerButton('Auto-Refresh: On'));

      expect(clearIntervalSpy).toHaveBeenCalledWith(intervalId);
      expect(footerButton('Auto-Refresh: Off')).toBeInTheDocument();
    });

    it('clears interval when modal is closed', async () => {
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');
      const { user } = await renderAndOpenLogs();

      await user.click(footerButton('Auto-Refresh: Off'));
      const intervalId = intervalIdFor(setIntervalSpy, 30000);
      await user.click(footerButton('Close'));

      expect(clearIntervalSpy).toHaveBeenCalledWith(intervalId);
    });

    it('clears interval on unmount', async () => {
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');
      const { user, unmount } = await renderAndOpenLogs();

      await user.click(footerButton('Auto-Refresh: Off'));
      const intervalId = intervalIdFor(setIntervalSpy, 30000);
      unmount();

      expect(clearIntervalSpy).toHaveBeenCalledWith(intervalId);
    });

    it('respects consoleLogRefreshIntervalMs setting', async () => {
      SETTINGS.consoleLogRefreshIntervalMs = 5000;
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      const { user } = await renderAndOpenLogs();

      expect(footerButton('Auto-Refresh: Off')).toHaveAttribute('title', 'Auto-refresh every 5s');
      await user.click(footerButton('Auto-Refresh: Off'));

      expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 5000);
    });
  });
});
