import { render, screen } from '@testing-library/react';
import React from 'react';
import { of as observableOf } from 'rxjs';

import { AccountService } from '../account/AccountService';
import { CloudProviderRegistry } from '../cloudProvider/CloudProviderRegistry';
import { SETTINGS } from '../config/settings';
import { Overridable } from './Overridable';
import { overridesComponent } from './Overrides';
import { overrideRegistry } from './override.registry';

class Original extends React.Component<{ accountId?: string }> {
  public render() {
    return <div>Original</div>;
  }
}

describe('Overridable', () => {
  it('renders a React component override registered in the override registry', () => {
    const key = 'overridable.spec.registryComponent';
    const OriginalComponent = Overridable(key)(Original);
    overrideRegistry.overrideComponent(key, () => <div>Override</div>);

    render(<OriginalComponent />);

    expect(screen.getByText('Override')).toBeInTheDocument();
    expect(screen.queryByText('Original')).not.toBeInTheDocument();
  });

  it('flushes queued override registrations into the singleton registry', () => {
    const key = 'overridable.spec.directSingletonRegistry';
    const OverrideComponent = () => <div>Override</div>;

    overridesComponent(OverrideComponent, key);

    expect(overrideRegistry.getComponent(key)).toBe(OverrideComponent as any);
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

    render(<OriginalComponent accountId="test" />);

    expect(screen.getByText('Original')).toBeInTheDocument();
  });

  it('renders a React component override registered for a cloud provider', () => {
    const key = 'overridable.spec.cloudProviderComponent';
    const provider = 'overridableSpecComponentProvider';
    const OriginalComponent = Overridable(key)(Original);
    SETTINGS.providers[provider] = { defaults: { account: 'test' } } as any;
    CloudProviderRegistry.registerProvider(provider, { name: provider });
    CloudProviderRegistry.overrideValue(provider, key, () => <div>Override</div>);
    AccountService.accounts$ = observableOf([{ name: 'test', cloudProvider: provider } as any]);

    render(<OriginalComponent accountId="test" />);

    expect(screen.getByText('Override')).toBeInTheDocument();
    expect(screen.queryByText('Original')).not.toBeInTheDocument();
  });

  it('prefers a cloud-provider override when a global override is also registered', () => {
    const key = 'overridable.spec.providerPrecedence';
    const provider = 'overridableSpecPrecedenceProvider';
    const OriginalComponent = Overridable(key)(Original);
    overrideRegistry.overrideComponent(key, () => <div>Global Override</div>);
    SETTINGS.providers[provider] = { defaults: { account: 'test' } } as any;
    CloudProviderRegistry.registerProvider(provider, { name: provider });
    CloudProviderRegistry.overrideValue(provider, key, () => <div>Provider Override</div>);
    AccountService.accounts$ = observableOf([{ name: 'test', cloudProvider: provider } as any]);

    render(<OriginalComponent accountId="test" />);

    expect(screen.getByText('Provider Override')).toBeInTheDocument();
    expect(screen.queryByText('Global Override')).not.toBeInTheDocument();
  });
});
