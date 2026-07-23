import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';
import { of as observableOf } from 'rxjs';

import { AccountService } from '../account/AccountService';
import { CloudProviderRegistry } from '../cloudProvider/CloudProviderRegistry';
import { SETTINGS } from '../config/settings';
import { REACT_MODULE } from '../reactShims';

import { Overridable } from './Overridable';
import { OVERRIDE_REGISTRY, OverrideRegistry } from './override.registry';

class Original extends React.Component<{ accountId?: string }> {
  public render() {
    return <div className="original">Original</div>;
  }
}

describe('Overridable', () => {
  beforeEach(mock.module(REACT_MODULE, OVERRIDE_REGISTRY));

  it('keeps deprecated template override methods as non-rendering compatibility shims', () => {
    const overrideRegistry = new OverrideRegistry();
    const warnSpy = spyOn(console, 'warn');

    expect(() => overrideRegistry.overrideTemplate('legacyTemplate', 'legacy.html')).not.toThrow();
    expect(() => overrideRegistry.overrideController('legacyController', 'LegacyController')).not.toThrow();

    expect(overrideRegistry.getTemplate('legacyTemplate')).toBeNull();
    expect(overrideRegistry.getController('legacyController')).toBeNull();
    expect(warnSpy.calls.count()).toBe(4);
    expect(warnSpy.calls.allArgs().map(([message]) => message)).toEqual([
      'OverrideRegistry.overrideTemplate("legacyTemplate") is deprecated. Angular template overrides are no longer rendered; migrate to overrideComponent(key, Component).',
      'OverrideRegistry.overrideController("legacyController") is deprecated. Angular controller overrides are no longer rendered; migrate to overrideComponent(key, Component).',
      'OverrideRegistry.getTemplate("legacyTemplate") is deprecated. Angular template overrides are no longer rendered; migrate to getComponent(key).',
      'OverrideRegistry.getController("legacyController") is deprecated. Angular controller overrides are no longer rendered; migrate to getComponent(key).',
    ]);
  });

  it('renders a React component override registered in the override registry', () => {
    const key = 'overridable.spec.registryComponent';
    const OriginalComponent = Overridable(key)(Original);
    const OverrideComponent = () => <div className="override">Override</div>;

    mock.inject((overrideRegistry: OverrideRegistry) => {
      overrideRegistry.overrideComponent(key, OverrideComponent);
    });

    const wrapper = mount(<OriginalComponent />);

    expect(wrapper.find('.override').text()).toBe('Override');
    expect(wrapper.find('.original').exists()).toBeFalse();
  });

  it('renders the original component when only a legacy cloud-provider template override is registered', () => {
    const key = 'overridable.spec.legacyCloudProviderTemplate';
    const provider = 'overridableSpecProvider';
    const OriginalComponent = Overridable(key)(Original);

    SETTINGS.providers[provider] = { defaults: { account: 'test' } } as any;
    CloudProviderRegistry.registerProvider(provider, { name: provider });
    CloudProviderRegistry.overrideValue(provider, `${key}TemplateUrl`, 'legacy-template.html');
    CloudProviderRegistry.overrideValue(provider, `${key}Controller`, 'LegacyController');
    AccountService.accounts$ = observableOf([{ name: 'test', cloudProvider: provider } as any]);

    const wrapper = mount(<OriginalComponent accountId="test" />);

    expect(wrapper.find('.original').text()).toBe('Original');
  });

  it('renders a React component override registered for a cloud provider', () => {
    const key = 'overridable.spec.cloudProviderComponent';
    const provider = 'overridableSpecComponentProvider';
    const OriginalComponent = Overridable(key)(Original);
    const OverrideComponent = () => <div className="override">Override</div>;

    SETTINGS.providers[provider] = { defaults: { account: 'test' } } as any;
    CloudProviderRegistry.registerProvider(provider, { name: provider });
    CloudProviderRegistry.overrideValue(provider, key, OverrideComponent);
    AccountService.accounts$ = observableOf([{ name: 'test', cloudProvider: provider } as any]);

    const wrapper = mount(<OriginalComponent accountId="test" />);

    expect(wrapper.find('.override').text()).toBe('Override');
    expect(wrapper.find('.original').exists()).toBeFalse();
  });
});
