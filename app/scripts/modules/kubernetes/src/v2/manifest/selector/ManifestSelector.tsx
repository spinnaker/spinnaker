import * as React from 'react';
import { Creatable, Option } from 'react-select';
import { IPromise } from 'angular';
import { Observable, Subject } from 'rxjs';
import { $q } from 'ngimport';

import { IAccountDetails, SETTINGS, StageConfigField, AccountSelectField, AccountService } from '@spinnaker/core';

import { IManifestSelector } from 'kubernetes/v2/manifest/selector/IManifestSelector';
import { ManifestKindSearchService } from 'kubernetes/v2/manifest/ManifestKindSearch';

export interface IManifestSelectorProps {
  selector: IManifestSelector;
  onChange(): void;
}

export interface IManifestSelectorState {
  accounts: IAccountDetails[];
  selector: IManifestSelector;
  namespaces: string[];
  kinds: string[];
  resources: string[];
  loading: boolean;
}

export class ManifestSelector extends React.Component<IManifestSelectorProps, IManifestSelectorState> {
  private search$ = new Subject<{ kind: string; namespace: string; account: string }>();
  private destroy$ = new Subject<void>();

  constructor(props: IManifestSelectorProps) {
    super(props);
    this.state = {
      selector: this.props.selector,
      accounts: [],
      namespaces: [],
      kinds: [],
      resources: [],
      loading: false,
    };
  }

  public componentDidMount = (): void => {
    this.loadAccounts();

    this.search$
      .do(() => this.setState({ loading: true }))
      .switchMap(({ kind, namespace, account }) => Observable.fromPromise(this.search(kind, namespace, account)))
      .takeUntil(this.destroy$)
      .subscribe(resources => {
        if (!(resources || []).some(resource => resource === this.state.selector.manifestName)) {
          this.handleNameChange('');
        }
        this.setState({ loading: false, resources: resources });
      });
  };

  public componentWillUnmount = () => this.destroy$.next();

  public loadAccounts = (): IPromise<void> => {
    return AccountService.getAllAccountDetailsForProvider('kubernetes', 'v2').then(accounts => {
      const selector = this.state.selector;
      const kind = this.parseSpinnakerName(selector.manifestName).kind;

      this.setState({ accounts });

      if (!selector.account && accounts.length > 0) {
        selector.account = accounts.some(e => e.name === SETTINGS.providers.kubernetes.defaults.account)
          ? SETTINGS.providers.kubernetes.defaults.account
          : accounts[0].name;
      }
      if (selector.account) {
        this.handleAccountChange(selector.account);
      }
      if (kind) {
        this.search$.next({ kind, namespace: selector.location, account: selector.account });
      }
    });
  };

  private handleAccountChange = (selectedAccount: string): void => {
    const details = (this.state.accounts || []).find(account => account.name === selectedAccount);
    if (!details) {
      return;
    }
    const namespaces = (details.namespaces || []).sort();
    const kinds = Object.keys(details.spinnakerKindMap || {}).sort();
    if (namespaces.every(ns => ns !== this.state.selector.location)) {
      this.state.selector.location = null;
    }
    this.state.selector.account = selectedAccount;

    this.search$.next({
      kind: this.parseSpinnakerName(this.state.selector.manifestName).kind,
      namespace: this.state.selector.location,
      account: this.state.selector.account,
    });
    this.setState({
      namespaces,
      kinds,
      selector: this.state.selector,
    });
    this.props.onChange();
  };

  private handleNamespaceChange = (selectedNamespace: Option): void => {
    this.state.selector.location =
      selectedNamespace && selectedNamespace.value ? (selectedNamespace.value as string) : null;
    this.search$.next({
      kind: this.parseSpinnakerName(this.state.selector.manifestName).kind,
      namespace: this.state.selector.location,
      account: this.state.selector.account,
    });
    this.setState({ selector: this.state.selector });
    this.props.onChange();
  };

  private handleKindChange = (selectedKind: Option): void => {
    const kind = selectedKind.value as string;
    const { name } = this.parseSpinnakerName(this.state.selector.manifestName);
    if (!kind) {
      this.state.selector.manifestName = name;
      this.setState({ resources: [], selector: this.state.selector });
      return;
    }

    this.state.selector.manifestName = name ? `${kind} ${name}` : kind;
    this.search$.next({ kind: kind, namespace: this.state.selector.location, account: this.state.selector.account });
    this.setState({ selector: this.state.selector });
    this.props.onChange();
  };

  private handleNameChange = (selectedName: string): void => {
    const { kind } = this.parseSpinnakerName(this.state.selector.manifestName);
    this.state.selector.manifestName = kind ? `${kind} ${selectedName}` : ` ${selectedName}`;
    this.setState({ selector: this.state.selector });
    this.props.onChange();
  };

  private parseSpinnakerName = (spinnakerName = ''): { name: string; kind: string } => {
    const [kind, name] = spinnakerName.split(' ');
    return { kind, name };
  };

  private isExpression = (value = ''): boolean => value.includes('${');

  private search = (kind: string, namespace: string, account: string): IPromise<string[]> => {
    if (this.isExpression(account)) {
      return $q.resolve([]);
    }
    return ManifestKindSearchService.search(kind, namespace, account).then(results =>
      results.map(result => result.name).sort(),
    );
  };

  public render() {
    const { selector, accounts, kinds, namespaces, resources, loading } = this.state;
    const { kind, name } = this.parseSpinnakerName(selector.manifestName);
    const resourceNames = resources.map(resource => this.parseSpinnakerName(resource).name);

    return (
      <>
        <StageConfigField label="Account">
          <AccountSelectField
            component={selector}
            field="account"
            accounts={accounts}
            onChange={this.handleAccountChange}
            provider="'kubernetes'"
          />
        </StageConfigField>
        <StageConfigField label="Namespace">
          <Creatable
            clearable={false}
            value={{ value: selector.location, label: selector.location }}
            options={namespaces.map(ns => ({ value: ns, label: ns }))}
            onChange={this.handleNamespaceChange}
          />
        </StageConfigField>
        <StageConfigField label="Kind">
          <Creatable
            clearable={false}
            value={{ value: kind, label: kind }}
            options={kinds.map(k => ({ value: k, label: k }))}
            onChange={this.handleKindChange}
          />
        </StageConfigField>
        <StageConfigField label="Name">
          <Creatable
            isLoading={loading}
            clearable={false}
            value={{ value: name, label: name }}
            options={resourceNames.map(r => ({ value: r, label: r }))}
            onChange={(option: Option) => this.handleNameChange(option ? (option.value as string) : null)}
          />
        </StageConfigField>
      </>
    );
  }
}
