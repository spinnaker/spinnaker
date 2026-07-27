import type { ShallowWrapper } from 'enzyme';
import { shallow } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { IAccountSelectInputProps, IAccountSelectInputState } from './AccountSelectInput';
import { AccountSelectInput } from './AccountSelectInput';
import type { IAccountDetails } from './AccountService';
import { AccountService } from './AccountService';
import Spy = jasmine.Spy;

const makeAccount = (name: string, cloudProvider: string, primaryAccount: boolean): IAccountDetails => {
  return {
    accountId: name,
    name,
    type: cloudProvider,
    cloudProvider,
    environment: null,
    primaryAccount,
    accountType: null,
    authorized: true,
    challengeDestructiveActions: false,
    regions: [],
    requiredGroupMembership: null,
  };
};

describe('<AccountSelectInput/>', () => {
  let component: ShallowWrapper<IAccountSelectInputProps, IAccountSelectInputState>;
  let AccountServiceSpy: Spy;

  const allAccounts: { [provider: string]: IAccountDetails[] } = {
    aws: [makeAccount('prod', 'aws', true), makeAccount('backup', 'aws', false)],
    titus: [makeAccount('titusprod', 'titus', true), makeAccount('titusbackup', 'titus', false)],
  };

  beforeEach(() => {
    AccountServiceSpy = spyOn(AccountService, 'getAllAccountDetailsForProvider').and.callFake((provider: string) => {
      return Promise.resolve(allAccounts[provider]);
    });
  });

  async function settleComponent(): Promise<void> {
    await act(async () => {
      await Promise.resolve();
    });
    component.update();
  }

  it('groups accounts by primary field when provider not specified', async () => {
    const accounts = allAccounts.aws.concat(allAccounts.titus);
    component = shallow(<AccountSelectInput accounts={accounts} provider={null} value="prod" />);
    await settleComponent();

    expect(component.state().primaryAccounts).toEqual(['prod', 'titusprod']);
    expect(component.state().secondaryAccounts).toEqual(['backup', 'titusbackup']);
  });

  it('groups accounts by primary field when only one provider available', async () => {
    component = shallow(<AccountSelectInput accounts={allAccounts.aws} provider={null} value="prod" />);
    await settleComponent();

    expect(component.state().primaryAccounts).toEqual(['prod']);
    expect(component.state().secondaryAccounts).toEqual(['backup']);
    expect(AccountServiceSpy.calls.count()).toBe(1);
  });

  it('groups accounts by primary field when only names and provider supplied', async () => {
    const accounts = allAccounts.aws.map((acct) => acct.name);
    component = shallow(<AccountSelectInput accounts={accounts} provider={'aws'} value="prod" />);
    await settleComponent();

    expect(component.state().primaryAccounts).toEqual(['prod']);
    expect(component.state().secondaryAccounts).toEqual(['backup']);
    expect(AccountServiceSpy.calls.count()).toBe(1);
  });

  it('sets mergedAccounts only if there are no accounts supplied', () => {
    component = shallow(<AccountSelectInput accounts={null} provider={null} value="" />);
    const state = component.state();

    expect(state.mergedAccounts).toEqual([]);
    expect(state.primaryAccounts).toEqual([]);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);
  });

  it('sets all accounts as primary when only names are supplied and provider is not set', async () => {
    component = shallow(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="prod" />);
    await settleComponent();
    const state = component.state();

    expect(state.mergedAccounts).toEqual(['prod', 'test']);
    expect(state.primaryAccounts).toEqual(['prod', 'test']);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);
  });

  it('re-groups accounts when they change', async () => {
    component = shallow(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="prod" />);
    await settleComponent();
    let state = component.state();

    expect(state.mergedAccounts).toEqual(['prod', 'test']);
    expect(state.primaryAccounts).toEqual(['prod', 'test']);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);

    component.setProps({ accounts: ['prod', 'test', 'staging'] });
    await settleComponent();
    state = component.state();

    expect(state.mergedAccounts).toEqual(['prod', 'staging', 'test']);
    expect(state.primaryAccounts).toEqual(['prod', 'staging', 'test']);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);
  });

  it('unselects nonexistent account', async function () {
    let updatedVal: string = null;
    const onChange = (evt: React.ChangeEvent<any>) => (updatedVal = evt.target.value);
    component = shallow(
      <AccountSelectInput accounts={['prod', 'test']} provider={null} value="nonexistent" onChange={onChange} />,
    );
    await settleComponent();
    expect(updatedVal).toBe('');
  });

  it('does not unselect account if account is an expression', () => {
    let updatedVal: string = null;
    const onChange = (evt: React.ChangeEvent<any>) => (updatedVal = evt.target.value);
    component = shallow(
      <AccountSelectInput
        accounts={['prod', 'test']}
        provider={null}
        value="${parameters.account}"
        onChange={onChange}
      />,
    );
    expect(updatedVal).toBeNull();
  });

  it('shows the runtime resolution notice when the account is an expression', () => {
    const text = 'Resolved at runtime from expression';
    component = shallow(
      <AccountSelectInput accounts={['prod', 'test']} provider={null} value="${parameters.account}" />,
    );
    expect(component.text()).toContain(text);
  });
});
