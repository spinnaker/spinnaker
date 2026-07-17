import { mount, shallow } from 'enzyme';
import React from 'react';

import { JobManifestPodLogs } from './JobManifestPodLogs';
import { InstanceReader } from '../../instance/InstanceReader';
import { SETTINGS } from '../../config/settings';
import type { IPodNameProvider } from '../PodNameProvider';

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

// Flush promise queue including chained .then() callbacks
const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('JobManifestPodLogs', () => {
  let getConsoleOutputSpy: jasmine.Spy;

  beforeEach(() => {
    getConsoleOutputSpy = spyOn(InstanceReader, 'getConsoleOutput').and.returnValue(
      Promise.resolve(mockConsoleOutput),
    );
    SETTINGS.consoleLogRefreshIntervalMs = 30000;
  });

  afterEach(() => {
    SETTINGS.resetToOriginal();
    try { jasmine.clock().uninstall(); } catch (_) {}
  });

  describe('rendering', () => {
    it('renders the link when all pod names are available', () => {
      const wrapper = shallow(<JobManifestPodLogs {...defaultProps} />);
      expect(wrapper.find('a.clickable').text()).toBe('Console Output');
    });

    it('renders nothing when a pod name is empty', () => {
      const wrapper = shallow(
        <JobManifestPodLogs {...defaultProps} podNamesProviders={[makeProvider('')]} />,
      );
      expect(wrapper.isEmptyRender()).toBe(true);
    });
  });

  describe('manual refresh', () => {
    it('fetches logs when the link is clicked', () => {
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      expect(getConsoleOutputSpy).toHaveBeenCalledWith('test-account', 'test-namespace', 'pod test-pod', 'kubernetes');
      wrapper.unmount();
    });

    it('stores container logs in state after logs load', async () => {
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      const instance = wrapper.instance() as JobManifestPodLogs;
      expect(instance.state.containerLogs.length).toBe(2);
      expect(instance.state.containerLogs[0].name).toBe('main');
      expect(instance.state.containerLogs[1].name).toBe('sidecar');
      wrapper.unmount();
    });

    it('re-fetches logs when refresh() is called directly', async () => {
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      getConsoleOutputSpy.calls.reset();
      (wrapper.instance() as JobManifestPodLogs).refresh();
      expect(getConsoleOutputSpy).toHaveBeenCalledTimes(1);
      wrapper.unmount();
    });

    it('preserves the selected tab across refreshes', async () => {
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      const instance = wrapper.instance() as JobManifestPodLogs;
      // Select second tab
      instance.selectLog(instance.state.containerLogs[1]);
      wrapper.update();
      expect(instance.state.selectedContainerLog.name).toBe('sidecar');

      // Refresh — selected tab should be preserved
      instance.refresh();
      await flushPromises();
      wrapper.update();
      expect(instance.state.selectedContainerLog.name).toBe('sidecar');
      wrapper.unmount();
    });
  });

  describe('auto-refresh toggle', () => {
    it('sets autoRefresh state to true when toggleAutoRefresh is called', async () => {
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      const instance = wrapper.instance() as JobManifestPodLogs;
      expect(instance.state.autoRefresh).toBe(false);
      instance.toggleAutoRefresh();
      wrapper.update();
      expect(instance.state.autoRefresh).toBe(true);
      instance.toggleAutoRefresh(); // clean up timer
      wrapper.unmount();
    });

    it('registers a setInterval with the configured refresh interval when auto-refresh is enabled', async () => {
      const setIntervalSpy = spyOn(window, 'setInterval').and.callThrough();
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      (wrapper.instance() as JobManifestPodLogs).toggleAutoRefresh();

      expect(setIntervalSpy).toHaveBeenCalledWith(jasmine.any(Function), 30000);
      (wrapper.instance() as JobManifestPodLogs).toggleAutoRefresh(); // clean up
      wrapper.unmount();
    });

    it('clears interval when auto-refresh is toggled off', async () => {
      const clearIntervalSpy = spyOn(window, 'clearInterval').and.callThrough();
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      const instance = wrapper.instance() as JobManifestPodLogs;
      instance.toggleAutoRefresh(); // on
      instance.toggleAutoRefresh(); // off

      expect(clearIntervalSpy).toHaveBeenCalled();
      wrapper.unmount();
    });

    it('clears interval when modal is closed', async () => {
      const clearIntervalSpy = spyOn(window, 'clearInterval').and.callThrough();
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      const instance = wrapper.instance() as JobManifestPodLogs;
      instance.toggleAutoRefresh();
      instance.close();

      expect(clearIntervalSpy).toHaveBeenCalled();
      wrapper.unmount();
    });

    it('clears interval on unmount', async () => {
      const clearIntervalSpy = spyOn(window, 'clearInterval').and.callThrough();
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      (wrapper.instance() as JobManifestPodLogs).toggleAutoRefresh();
      wrapper.unmount();

      expect(clearIntervalSpy).toHaveBeenCalled();
    });

    it('respects consoleLogRefreshIntervalMs setting', async () => {
      SETTINGS.consoleLogRefreshIntervalMs = 5000;
      const setIntervalSpy = spyOn(window, 'setInterval').and.callThrough();
      const wrapper = mount(<JobManifestPodLogs {...defaultProps} />);
      wrapper.find('a.clickable').simulate('click');
      await flushPromises();
      wrapper.update();

      (wrapper.instance() as JobManifestPodLogs).toggleAutoRefresh();

      expect(setIntervalSpy).toHaveBeenCalledWith(jasmine.any(Function), 5000);
      (wrapper.instance() as JobManifestPodLogs).toggleAutoRefresh(); // clean up
      wrapper.unmount();
    });
  });
});
