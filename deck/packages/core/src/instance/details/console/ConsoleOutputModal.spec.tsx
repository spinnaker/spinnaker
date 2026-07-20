import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { ConsoleOutputModal } from './ConsoleOutputModal';
import { InstanceReader } from '../../InstanceReader';
import { ModalContext } from '../../../presentation/modal/ModalContext';
import { SETTINGS } from '../../../config/settings';
import type { IInstance } from '../../../domain';

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

// Flush microtask queue so useData resolves
const flushMicrotasks = () =>
  act(async () => {
    await Promise.resolve();
  });

function makeWrapper(usesMultiOutput = false) {
  return mount(
    wrapWithModalContext(
      <ConsoleOutputModal instance={mockInstance} usesMultiOutput={usesMultiOutput} dismissModal={() => {}} />,
    ),
  );
}

describe('ConsoleOutputModal', () => {
  let getConsoleOutputSpy: jasmine.Spy;

  beforeEach(() => {
    getConsoleOutputSpy = spyOn(InstanceReader, 'getConsoleOutput').and.returnValue(Promise.resolve(singleOutput));
    SETTINGS.consoleLogRefreshIntervalMs = 30000;
  });

  afterEach(() => {
    SETTINGS.resetToOriginal();
    try {
      jasmine.clock().uninstall();
    } catch (_) {}
  });

  describe('rendering', () => {
    it('shows a spinner while loading', () => {
      getConsoleOutputSpy.and.returnValue(new Promise(() => {}));
      const wrapper = makeWrapper();
      expect(wrapper.find('Spinner').exists()).toBe(true);
      wrapper.unmount();
    });

    it('renders single-output log content when not multi-output', async () => {
      const wrapper = makeWrapper();
      await flushMicrotasks();
      wrapper.update();
      expect(wrapper.find('pre').text()).toContain('some log output');
      wrapper.unmount();
    });

    it('renders tabs for multi-output logs', async () => {
      getConsoleOutputSpy.and.returnValue(Promise.resolve(multiOutput));
      const wrapper = makeWrapper(true);
      await flushMicrotasks();
      wrapper.update();
      const tabs = wrapper.find('.console-output-tab');
      expect(tabs.length).toBe(2);
      expect(tabs.at(0).text()).toBe('container-1');
      expect(tabs.at(1).text()).toBe('container-2');
      wrapper.unmount();
    });

    it('shows Refresh and Auto-Refresh buttons when output is available', async () => {
      const wrapper = makeWrapper();
      await flushMicrotasks();
      wrapper.update();
      const labels = wrapper.find('button').map((b: any) => b.text());
      expect(labels).toContain('Refresh');
      expect(labels).toContain('Auto-Refresh: Off');
      wrapper.unmount();
    });

    it('does not show Refresh or Auto-Refresh buttons while loading', () => {
      getConsoleOutputSpy.and.returnValue(new Promise(() => {}));
      const wrapper = makeWrapper();
      const labels = wrapper.find('button').map((b: any) => b.text());
      expect(labels).not.toContain('Refresh');
      expect(labels).not.toContain('Auto-Refresh: Off');
      wrapper.unmount();
    });
  });

  describe('manual refresh', () => {
    it('calls getConsoleOutput again when Refresh button is clicked', async () => {
      const wrapper = makeWrapper();
      await flushMicrotasks();
      wrapper.update();

      getConsoleOutputSpy.calls.reset();
      wrapper
        .find('button')
        .filterWhere((b: any) => b.text() === 'Refresh')
        .simulate('click');
      expect(getConsoleOutputSpy).toHaveBeenCalledTimes(1);
      wrapper.unmount();
    });
  });

  describe('auto-refresh toggle', () => {
    it('toggles button label when Auto-Refresh is clicked', async () => {
      const wrapper = makeWrapper();
      await flushMicrotasks();
      wrapper.update();

      const autoRefreshBtn = () => wrapper.find('button').filterWhere((b: any) => b.text().startsWith('Auto-Refresh'));
      expect(autoRefreshBtn().text()).toBe('Auto-Refresh: Off');

      // Use act to flush state update from the click
      act(() => {
        autoRefreshBtn().simulate('click');
      });
      wrapper.update();
      expect(autoRefreshBtn().text()).toBe('Auto-Refresh: On');
      wrapper.unmount();
    });

    it('registers a setInterval with the configured refresh interval when auto-refresh is enabled', async () => {
      const setIntervalSpy = spyOn(window, 'setInterval').and.callThrough();
      const wrapper = makeWrapper();
      await flushMicrotasks();
      wrapper.update();

      act(() => {
        wrapper
          .find('button')
          .filterWhere((b: any) => b.text() === 'Auto-Refresh: Off')
          .simulate('click');
      });
      wrapper.update();

      expect(setIntervalSpy).toHaveBeenCalledWith(jasmine.any(Function), 30000);
      wrapper.unmount();
    });

    it('clears the interval when auto-refresh is toggled off', async () => {
      const clearIntervalSpy = spyOn(window, 'clearInterval').and.callThrough();
      const wrapper = makeWrapper();
      await flushMicrotasks();
      wrapper.update();

      const autoRefreshBtn = () => wrapper.find('button').filterWhere((b: any) => b.text().startsWith('Auto-Refresh'));

      act(() => {
        autoRefreshBtn().simulate('click');
      }); // on
      wrapper.update();
      act(() => {
        autoRefreshBtn().simulate('click');
      }); // off
      wrapper.update();

      expect(clearIntervalSpy).toHaveBeenCalled();
      wrapper.unmount();
    });

    it('respects consoleLogRefreshIntervalMs setting', async () => {
      SETTINGS.consoleLogRefreshIntervalMs = 10000;
      const setIntervalSpy = spyOn(window, 'setInterval').and.callThrough();
      const wrapper = makeWrapper();
      await flushMicrotasks();
      wrapper.update();

      act(() => {
        wrapper
          .find('button')
          .filterWhere((b: any) => b.text() === 'Auto-Refresh: Off')
          .simulate('click');
      });
      wrapper.update();

      expect(setIntervalSpy).toHaveBeenCalledWith(jasmine.any(Function), 10000);
      wrapper.unmount();
    });
  });
});
