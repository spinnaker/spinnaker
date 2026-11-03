import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import { ConsoleOutputModal } from './ConsoleOutputModal';
import { InstanceReader } from '../../InstanceReader';
import { SETTINGS } from '../../../config/settings';
import type { IInstance } from '../../../domain';
import { ModalContext } from '../../../presentation/modal/ModalContext';
import { setupUser } from '../../../utils/testUtils';

const mockInstance: IInstance = {
  account: 'test-account',
  region: 'us-east-1',
  id: 'i-abc123',
  provider: 'kubernetes',
} as IInstance;

const singleOutput = { output: 'some log output' };
const multiOutput = {
  output: [
    { name: 'container-1', output: 'log line 1' },
    { name: 'container-2', output: 'log line 2' },
  ],
};

const modalContextValue = { onRequestClose: () => {} };
function wrapWithModalContext(node: React.ReactElement) {
  return <ModalContext.Provider value={modalContextValue}>{node}</ModalContext.Provider>;
}

function renderModal(usesMultiOutput = false) {
  return render(
    wrapWithModalContext(
      <ConsoleOutputModal instance={mockInstance} usesMultiOutput={usesMultiOutput} dismissModal={() => {}} />,
    ),
  );
}

const findButton = (name: string) => screen.findByRole('button', { name });

describe('ConsoleOutputModal', () => {
  let getConsoleOutputSpy: Mock;

  beforeEach(() => {
    getConsoleOutputSpy = vi.spyOn(InstanceReader, 'getConsoleOutput').mockReturnValue(Promise.resolve(singleOutput));
    SETTINGS.consoleLogRefreshIntervalMs = 30000;
  });

  afterEach(() => {
    SETTINGS.resetToOriginal();
  });

  describe('rendering', () => {
    it('shows a spinner while loading', () => {
      getConsoleOutputSpy.mockReturnValue(new Promise(() => {}));
      const { container } = renderModal();
      expect(container.querySelector('.spinner-container .load')).toBeInTheDocument();
    });

    it('renders single-output log content when not multi-output', async () => {
      renderModal();
      const log = await screen.findByText('some log output');
      expect(log.tagName).toBe('PRE');
    });

    it('renders tabs for multi-output logs', async () => {
      getConsoleOutputSpy.mockReturnValue(Promise.resolve(multiOutput));
      const { container } = renderModal(true);
      await screen.findByText('container-1');
      const tabs = Array.from(container.querySelectorAll('.console-output-tab'));
      expect(tabs.map((tab) => tab.textContent)).toEqual(['container-1', 'container-2']);
    });

    it('shows Refresh and Auto-Refresh buttons when output is available', async () => {
      renderModal();
      expect(await findButton('Refresh')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Auto-Refresh: Off' })).toBeInTheDocument();
    });

    it('does not show Refresh or Auto-Refresh buttons while loading', () => {
      getConsoleOutputSpy.mockReturnValue(new Promise(() => {}));
      renderModal();
      expect(screen.queryByRole('button', { name: 'Refresh' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Auto-Refresh: Off' })).not.toBeInTheDocument();
    });
  });

  describe('manual refresh', () => {
    it('calls getConsoleOutput again when Refresh button is clicked', async () => {
      const user = setupUser();
      renderModal();
      const refresh = await findButton('Refresh');

      getConsoleOutputSpy.mockClear();
      await user.click(refresh);
      await waitFor(() => expect(getConsoleOutputSpy).toHaveBeenCalledTimes(1));
    });
  });

  describe('auto-refresh toggle', () => {
    it('toggles button label when Auto-Refresh is clicked', async () => {
      const user = setupUser();
      renderModal();

      await user.click(await findButton('Auto-Refresh: Off'));
      expect(screen.getByRole('button', { name: 'Auto-Refresh: On' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Auto-Refresh: Off' })).not.toBeInTheDocument();
    });

    it('registers a setInterval with the configured refresh interval when auto-refresh is enabled', async () => {
      const user = setupUser();
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      renderModal();

      await user.click(await findButton('Auto-Refresh: Off'));

      await waitFor(() => expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 30000));
    });

    it('clears the interval when auto-refresh is toggled off', async () => {
      const user = setupUser();
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      const clearIntervalSpy = vi.spyOn(globalThis, 'clearInterval');
      renderModal();

      await user.click(await findButton('Auto-Refresh: Off'));
      await waitFor(() => expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 30000));
      const autoRefreshCall = setIntervalSpy.mock.calls.findIndex(([, delay]) => delay === 30000);
      const intervalId = setIntervalSpy.mock.results[autoRefreshCall].value;

      await user.click(screen.getByRole('button', { name: 'Auto-Refresh: On' }));

      expect(screen.getByRole('button', { name: 'Auto-Refresh: Off' })).toBeInTheDocument();
      await waitFor(() => expect(clearIntervalSpy).toHaveBeenCalledWith(intervalId));
    });

    it('respects consoleLogRefreshIntervalMs setting', async () => {
      const user = setupUser();
      SETTINGS.consoleLogRefreshIntervalMs = 10000;
      const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');
      renderModal();

      await user.click(await findButton('Auto-Refresh: Off'));

      await waitFor(() => expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 10000));
    });
  });
});
