import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { AccountSelectInput } from './AccountSelectInput';
import type { IAccountDetails } from './AccountService';
import { AccountService } from './AccountService';

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
  let AccountServiceSpy: ReturnType<typeof vi.spyOn>;

  const allAccounts: { [provider: string]: IAccountDetails[] } = {
    aws: [makeAccount('prod', 'aws', true), makeAccount('backup', 'aws', false)],
    kubernetes: [makeAccount('prodk8s', 'kubernetes', true), makeAccount('backupk8s', 'kubernetes', false)],
  };

  beforeEach(() => {
    AccountServiceSpy = vi
      .spyOn(AccountService, 'getAllAccountDetailsForProvider')
      .mockImplementation((provider: string) => {
        return Promise.resolve(allAccounts[provider]);
      });
  });

  const optionValues = () => screen.getAllByRole('option').map((option) => (option as HTMLOptionElement).value);

  it('groups accounts by primary field when provider not specified', async () => {
    const accounts = allAccounts.aws.concat(allAccounts.kubernetes);
    render(<AccountSelectInput accounts={accounts} provider={null} value="prod" />);

    await waitFor(() => expect(optionValues()).toEqual(['', 'prod', 'prodk8s', '-', 'backup', 'backupk8s']));
  });

  it('groups accounts by primary field when only one provider available', async () => {
    render(<AccountSelectInput accounts={allAccounts.aws} provider={null} value="prod" />);

    await waitFor(() => expect(optionValues()).toEqual(['', 'prod', '-', 'backup']));
    expect(AccountServiceSpy.mock.calls.length).toBe(1);
  });

  it('groups accounts by primary field when only names and provider supplied', async () => {
    const accounts = allAccounts.aws.map((acct) => acct.name);
    render(<AccountSelectInput accounts={accounts} provider="aws" value="prod" />);

    await waitFor(() => expect(optionValues()).toEqual(['', 'prod', '-', 'backup']));
    expect(AccountServiceSpy.mock.calls.length).toBe(1);
  });

  it('sets mergedAccounts only if there are no accounts supplied', () => {
    render(<AccountSelectInput accounts={null} provider={null} value="" />);

    expect(optionValues()).toEqual(['']);
    expect(AccountServiceSpy.mock.calls.length).toBe(0);
  });

  it('sets all accounts as primary when only names are supplied and provider is not set', async () => {
    render(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="prod" />);

    await waitFor(() => expect(optionValues()).toEqual(['', 'prod', 'test']));
    expect(AccountServiceSpy.mock.calls.length).toBe(0);
  });

  it('re-groups accounts when they change', async () => {
    const { rerender } = render(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="prod" />);

    await waitFor(() => expect(optionValues()).toEqual(['', 'prod', 'test']));
    expect(AccountServiceSpy.mock.calls.length).toBe(0);

    rerender(<AccountSelectInput accounts={['prod', 'test', 'staging']} provider={null} value="prod" />);

    await waitFor(() => expect(optionValues()).toEqual(['', 'prod', 'staging', 'test']));
    expect(AccountServiceSpy.mock.calls.length).toBe(0);
  });

  it('unselects nonexistent account', async function () {
    let updatedVal: string = null;
    const onChange = (evt: React.ChangeEvent<any>) => (updatedVal = evt.target.value);
    render(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="nonexistent" onChange={onChange} />);
    await waitFor(() => expect(updatedVal).toBe(''));
  });

  it('does not unselect account if account is an expression', () => {
    let updatedVal: string = null;
    const onChange = (evt: React.ChangeEvent<any>) => (updatedVal = evt.target.value);
    render(
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
    render(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="${parameters.account}" />);
    expect(screen.getByText((content) => content.includes(text))).toBeInTheDocument();
  });
});
