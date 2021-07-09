import React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { IScope, mock } from 'angular';
import { AccountService, IAccountDetails } from './AccountService';
import { AccountSelectInput, IAccountSelectInputProps, IAccountSelectInputState } from './AccountSelectInput';
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
  let $scope: IScope;
  let AccountServiceSpy: Spy;

  const allAccounts: { [provider: string]: IAccountDetails[] } = {
    aws: [makeAccount('prod', 'aws', true), makeAccount('backup', 'aws', false)],
    titus: [makeAccount('titusprod', 'titus', true), makeAccount('titusbackup', 'titus', false)],
  };

  beforeEach(
    mock.inject(($rootScope: IScope) => {
      $scope = $rootScope.$new();
      AccountServiceSpy = spyOn(AccountService, 'getAllAccountDetailsForProvider').and.callFake((provider: string) => {
        return Promise.resolve(allAccounts[provider]);
      });
    }),
  );

  it('groups accounts by primary field when provider not specified', (done) => {
    const accounts = allAccounts.aws.concat(allAccounts.titus);
    component = shallow(<AccountSelectInput accounts={accounts} provider={null} value="prod" />);
    setImmediate(() => {
      $scope.$digest();

      expect(component.state().primaryAccounts).toEqual(['prod', 'titusprod']);
      expect(component.state().secondaryAccounts).toEqual(['backup', 'titusbackup']);
      done();
    });
  });

  it('groups accounts by primary field when only one provider available', (done) => {
    component = shallow(<AccountSelectInput accounts={allAccounts.aws} provider={null} value="prod" />);
    setImmediate(() => {
      $scope.$digest();

      expect(component.state().primaryAccounts).toEqual(['prod']);
      expect(component.state().secondaryAccounts).toEqual(['backup']);
      expect(AccountServiceSpy.calls.count()).toBe(1);
      done();
    });
  });

  it('groups accounts by primary field when only names and provider supplied', (done) => {
    const accounts = allAccounts.aws.map((acct) => acct.name);
    component = shallow(<AccountSelectInput accounts={accounts} provider={'aws'} value="prod" />);
    setImmediate(() => {
      $scope.$digest();

      expect(component.state().primaryAccounts).toEqual(['prod']);
      expect(component.state().secondaryAccounts).toEqual(['backup']);
      expect(AccountServiceSpy.calls.count()).toBe(1);
      done();
    });
  });

  it('sets mergedAccounts only if there are no accounts supplied', () => {
    component = shallow(<AccountSelectInput accounts={null} provider={null} value="" />);
    $scope.$digest();
    const state = component.state();

    expect(state.mergedAccounts).toEqual([]);
    expect(state.primaryAccounts).toEqual([]);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);
  });

  it('sets all accounts as primary when only names are supplied and provider is not set', () => {
    component = shallow(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="prod" />);
    $scope.$digest();
    const state = component.state();

    expect(state.mergedAccounts).toEqual(['prod', 'test']);
    expect(state.primaryAccounts).toEqual(['prod', 'test']);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);
  });

  it('re-groups accounts when they change', () => {
    component = shallow(<AccountSelectInput accounts={['prod', 'test']} provider={null} value="prod" />);
    $scope.$digest();
    let state = component.state();

    expect(state.mergedAccounts).toEqual(['prod', 'test']);
    expect(state.primaryAccounts).toEqual(['prod', 'test']);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);

    component.setProps({ accounts: ['prod', 'test', 'staging'] });
    $scope.$digest();
    state = component.state();

    expect(state.mergedAccounts).toEqual(['prod', 'staging', 'test']);
    expect(state.primaryAccounts).toEqual(['prod', 'staging', 'test']);
    expect(state.secondaryAccounts).toEqual([]);
    expect(AccountServiceSpy.calls.count()).toBe(0);
  });

  it('unselects nonexistent account', function () {
    let updatedVal: string = null;
    const onChange = (evt: React.ChangeEvent<any>) => (updatedVal = evt.target.value);
    component = shallow(
      <AccountSelectInput accounts={['prod', 'test']} provider={null} value="nonexistent" onChange={onChange} />,
    );
    $scope.$digest();
    expect(updatedVal).toBe('');
  });
  //
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
    $scope.$digest();
    expect(updatedVal).toBeNull();
  });

  it('sets flag on ctrl if account is an expression', () => {
    const text = 'Resolved at runtime from expression';
    component = shallow(
      <AccountSelectInput accounts={['prod', 'test']} provider={null} value="${parameters.account}" />,
    );
    $scope.$digest();
    expect(component.text()).toContain(text);
  });
});
