import { get, isEmpty } from 'lodash';
import { $q } from 'ngimport';
import React from 'react';
import Select, { Creatable, Option } from 'react-select';
import { from as observableFrom, Subject } from 'rxjs';
import { switchMap, takeUntil, tap } from 'rxjs/operators';

import {
  AccountSelectInput,
  AccountService,
  Application,
  AppListExtractor,
  IAccountDetails,
  IServerGroup,
  NgReact,
  noop,
  ScopeClusterSelector,
  SETTINGS,
  StageConfigField,
  StageConstants,
} from '@spinnaker/core';

import { IManifestLabelSelector } from './IManifestLabelSelector';
import { IManifestSelector, SelectorMode, SelectorModeDataMap } from './IManifestSelector';
import { ManifestKindSearchService } from '../ManifestKindSearch';
import LabelEditor from './labelEditor/LabelEditor';

export interface IManifestSelectorProps {
  selector: IManifestSelector;
  application?: Application;
  includeSpinnakerKinds?: string[];
  modes?: SelectorMode[];
  onChange(selector: IManifestSelector): void;
}

export interface IManifestSelectorState {
  accounts: IAccountDetails[];
  selector: IManifestSelector;
  namespaces: string[];
  kinds: string[];
  resources: string[];
  loading: boolean;
}

interface ISelectorHandler {
  handles(mode: SelectorMode): boolean;
  handleModeChange(): void;
  handleKindChange(kind: string): void;
  getKind(): string;
}

const parseSpinnakerName = (spinnakerName: string): { name: string; kind: string } => {
  const [kind, name] = (spinnakerName || '').split(' ');
  return { kind, name };
};

class StaticManifestSelectorHandler implements ISelectorHandler {
  constructor(private component: ManifestSelector) {}

  public handles = (mode: SelectorMode): boolean => mode === SelectorMode.Static;

  public handleModeChange = (): void => {
    const { selector } = this.component.state;
    this.handleKindChange(selector.kind);
    Object.assign(selector, SelectorModeDataMap.static.selectorDefaults);
    this.component.setStateAndUpdateStage({ selector });
  };

  public handleKindChange = (kind: string): void => {
    const { selector } = this.component.state;
    const { name } = parseSpinnakerName(selector.manifestName);
    selector.manifestName = kind ? (name ? `${kind} ${name}` : kind) : name;
  };

  public getKind = (): string => parseSpinnakerName(this.component.state.selector.manifestName).kind;
}

class DynamicManifestSelectorHandler implements ISelectorHandler {
  constructor(private component: ManifestSelector) {}

  public handles = (mode: SelectorMode): boolean => mode === SelectorMode.Dynamic;

  public handleModeChange = (): void => {
    const { selector } = this.component.state;
    const { kind } = parseSpinnakerName(selector.manifestName);
    selector.kind = kind || null;
    Object.assign(selector, SelectorModeDataMap.dynamic.selectorDefaults);
    this.component.setStateAndUpdateStage({ selector });
  };

  public handleKindChange = (kind: string): void => {
    this.component.state.selector.kind = kind;
  };

  public getKind = (): string => this.component.state.selector.kind;
}

class LabelManifestSelectorHandler implements ISelectorHandler {
  constructor(private component: ManifestSelector) {}

  public handles = (mode: SelectorMode): boolean => mode === SelectorMode.Label;

  public handleModeChange = (): void => {
    const { selector } = this.component.state;
    Object.assign(selector, SelectorModeDataMap.label.selectorDefaults);
    this.component.setStateAndUpdateStage({ selector });
  };

  public handleKindChange = (): void => {};

  public getKind = (): string => null;
}

export class ManifestSelector extends React.Component<IManifestSelectorProps, IManifestSelectorState> {
  private search$ = new Subject<{ kind: string; namespace: string; account: string }>();
  private destroy$ = new Subject<void>();
  private handlers: ISelectorHandler[];

  constructor(props: IManifestSelectorProps) {
    super(props);

    if (!this.props.selector.mode) {
      this.props.selector.mode = SelectorMode.Static;
      this.props.onChange && this.props.onChange(this.props.selector);
    }

    this.state = {
      selector: props.selector,
      accounts: [],
      namespaces: [],
      kinds: [],
      resources: [],
      loading: false,
    };
    this.handlers = [
      new StaticManifestSelectorHandler(this),
      new DynamicManifestSelectorHandler(this),
      new LabelManifestSelectorHandler(this),
    ];
  }

  public setStateAndUpdateStage = (state: Partial<IManifestSelectorState>, cb?: () => void): void => {
    if (state.selector && this.props.onChange) {
      this.props.onChange(state.selector);
    }
    this.setState(state as IManifestSelectorState, cb || noop);
  };

  public componentDidMount = (): void => {
    this.loadAccounts();

    this.search$
      .pipe(
        tap(() => this.setStateAndUpdateStage({ loading: true })),
        switchMap(({ kind, namespace, account }) => observableFrom(this.search(kind, namespace, account))),
        takeUntil(this.destroy$),
      )
      .subscribe((resources) => {
        if (this.state.selector.manifestName == null && this.getSelectedMode() === SelectorMode.Static) {
          this.handleNameChange('');
        }
        this.setStateAndUpdateStage({ loading: false, resources: resources, selector: this.state.selector });
      });
  };

  public componentWillUnmount = () => this.destroy$.next();

  public loadAccounts = (): PromiseLike<void> => {
    return AccountService.getAllAccountDetailsForProvider('kubernetes').then((accounts) => {
      const selector = this.state.selector;
      const kind = parseSpinnakerName(selector.manifestName).kind;

      this.setStateAndUpdateStage({ accounts });

      if (!selector.account && accounts.length > 0) {
        selector.account = accounts.some((e) => e.name === SETTINGS.providers.kubernetes.defaults.account)
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
    const details = (this.state.accounts || []).find((account) => account.name === selectedAccount);
    if (!details) {
      return;
    }
    const namespaces = (details.namespaces || []).sort();
    const kinds = Object.entries(details.spinnakerKindMap || {})
      .filter(([, spinnakerKind]) =>
        this.props.includeSpinnakerKinds && this.props.includeSpinnakerKinds.length
          ? this.props.includeSpinnakerKinds.includes(spinnakerKind)
          : true,
      )
      .map(([kind]) => kind)
      .sort();

    if (
      !this.isExpression(this.state.selector.location) &&
      namespaces.every((ns) => ns !== this.state.selector.location)
    ) {
      this.state.selector.location = null;
    }
    this.state.selector.account = selectedAccount;

    this.search$.next({
      kind: parseSpinnakerName(this.state.selector.manifestName).kind || this.state.selector.kind,
      namespace: this.state.selector.location,
      account: this.state.selector.account,
    });
    this.setStateAndUpdateStage({
      namespaces,
      kinds,
      selector: this.state.selector,
    });
  };

  private handleNamespaceChange = (selectedNamespace: Option): void => {
    this.state.selector.location =
      selectedNamespace && selectedNamespace.value ? (selectedNamespace.value as string) : null;
    this.search$.next({
      kind: parseSpinnakerName(this.state.selector.manifestName).kind,
      namespace: this.state.selector.location,
      account: this.state.selector.account,
    });
    this.setStateAndUpdateStage({ selector: this.state.selector });
  };

  public handleKindChange = (kind: string): void => {
    this.modeDelegate().handleKindChange(kind);
    this.search$.next({ kind: kind, namespace: this.state.selector.location, account: this.state.selector.account });
  };

  private handleNameChange = (selectedName: string): void => {
    const { kind } = parseSpinnakerName(this.state.selector.manifestName);
    this.state.selector.manifestName = kind ? `${kind} ${selectedName}` : ` ${selectedName}`;
    this.setStateAndUpdateStage({ selector: this.state.selector });
  };

  private isExpression = (value: string): boolean => (typeof value === 'string' ? value.includes('${') : false);

  private search = (kind: string, namespace: string, account: string): PromiseLike<string[]> => {
    if (this.isExpression(account)) {
      return $q.resolve([]);
    }
    return ManifestKindSearchService.search(kind, namespace, account).then((results) =>
      results.map((result) => result.name).sort(),
    );
  };

  private handleModeSelect = (mode: SelectorMode) => {
    this.state.selector.mode = mode;
    this.setStateAndUpdateStage({ selector: this.state.selector }, () => {
      this.modeDelegate().handleModeChange();
    });
  };

  private handleClusterChange = ({ clusterName }: { clusterName: string }) => {
    this.state.selector.cluster = clusterName;
    this.setStateAndUpdateStage({ selector: this.state.selector });
  };

  private handleCriteriaChange = (criteria: string) => {
    this.state.selector.criteria = criteria;
    this.setStateAndUpdateStage({ selector: this.state.selector });
  };

  public handleKindsChange = (kinds: string[]): void => {
    this.state.selector.kinds = kinds;
    this.setStateAndUpdateStage({ selector: this.state.selector });
  };

  public handleLabelSelectorsChange = (labelSelectors: IManifestLabelSelector[]): void => {
    this.state.selector.labelSelectors.selectors = labelSelectors;
    this.setStateAndUpdateStage({ selector: this.state.selector });
  };

  private modeDelegate = (): ISelectorHandler =>
    this.handlers.find((handler) => handler.handles(this.getSelectedMode()));

  private promptTextCreator = (text: string) => `Use custom expression: ${text}`;

  private getSelectedMode = (): SelectorMode => this.state.selector.mode || SelectorMode.Static;

  private getFilteredClusters = (): string[] => {
    const { application, includeSpinnakerKinds } = this.props;
    const { selector } = this.state;
    const applications = application ? [application] : [];
    // If the only allowlisted Spinnaker kind is `serverGroups`, exclude server groups with `serverGroupManagers`.
    // This is because traffic management stages only allow ReplicaSets.
    const includeServerGroupsWithManagers: boolean =
      isEmpty(includeSpinnakerKinds) || includeSpinnakerKinds.length > 1 || includeSpinnakerKinds[0] !== 'serverGroups';
    const filter = (serverGroup: IServerGroup): boolean => {
      const accountAndNamespaceFilter: boolean = AppListExtractor.clusterFilterForCredentialsAndRegion(
        selector.account,
        selector.location,
      )(serverGroup);
      const hasServerGroupManagers: boolean = get(serverGroup, 'serverGroupManagers.length', 0) > 0;
      const serverGroupManagerFilter: boolean = includeServerGroupsWithManagers || !hasServerGroupManagers;
      const nameToParseKind: string = hasServerGroupManagers ? serverGroup.cluster : serverGroup.name;
      const kindFilter: boolean = parseSpinnakerName(nameToParseKind).kind === this.modeDelegate().getKind();
      return accountAndNamespaceFilter && serverGroupManagerFilter && kindFilter;
    };
    return AppListExtractor.getClusters(applications, filter);
  };

  public render() {
    const { TargetSelect } = NgReact;
    const selectedMode = this.getSelectedMode();
    const modes = this.props.modes || [selectedMode];
    const { selector, accounts, kinds, namespaces, resources, loading } = this.state;
    const kind = this.modeDelegate().getKind();
    const name = parseSpinnakerName(selector.manifestName).name;
    const resourceNames = resources.map((resource) => parseSpinnakerName(resource).name);
    const selectedKinds = selector.kinds || [];
    const KindField = (
      <StageConfigField label="Kind">
        <Creatable
          clearable={false}
          value={{ value: kind, label: kind }}
          options={kinds.map((k) => ({ value: k, label: k }))}
          onChange={(option: Option<string>) => this.handleKindChange(option && option.value)}
          promptTextCreator={this.promptTextCreator}
        />
      </StageConfigField>
    );

    return (
      <>
        <StageConfigField label="Account">
          <AccountSelectInput
            value={selector.account}
            onChange={(evt: any) => this.handleAccountChange(evt.target.value)}
            accounts={accounts}
            provider="'kubernetes'"
          />
        </StageConfigField>
        <StageConfigField label="Namespace">
          <Creatable
            clearable={false}
            value={{ value: selector.location, label: selector.location }}
            options={namespaces.map((ns) => ({ value: ns, label: ns }))}
            onChange={this.handleNamespaceChange}
            promptTextCreator={this.promptTextCreator}
          />
        </StageConfigField>
        {!modes.includes(SelectorMode.Label) && KindField}
        {modes.length > 1 && (
          <StageConfigField label="Selector">
            {modes.map((mode) => (
              <div className="radio" key={mode}>
                <label htmlFor={mode}>
                  <input
                    type="radio"
                    onChange={() => this.handleModeSelect(mode)}
                    checked={selectedMode === mode}
                    id={mode}
                  />{' '}
                  {get(SelectorModeDataMap, [mode, 'label'], '')}
                </label>
              </div>
            ))}
          </StageConfigField>
        )}
        {modes.includes(SelectorMode.Label) && selectedMode !== SelectorMode.Label && KindField}
        {modes.includes(SelectorMode.Static) && selectedMode === SelectorMode.Static && (
          <StageConfigField label="Name">
            <Creatable
              isLoading={loading}
              clearable={false}
              value={{ value: name, label: name }}
              options={resourceNames.map((r) => ({ value: r, label: r }))}
              onChange={(option: Option) => this.handleNameChange(option ? (option.value as string) : '')}
              promptTextCreator={this.promptTextCreator}
            />
          </StageConfigField>
        )}
        {modes.includes(SelectorMode.Dynamic) && selectedMode === SelectorMode.Dynamic && (
          <>
            <StageConfigField label="Cluster">
              <ScopeClusterSelector
                clusters={this.getFilteredClusters()}
                model={selector.cluster}
                onChange={this.handleClusterChange}
              />
            </StageConfigField>
            <StageConfigField label="Target">
              <TargetSelect
                onChange={this.handleCriteriaChange}
                model={{ target: selector.criteria }}
                options={StageConstants.MANIFEST_CRITERIA_OPTIONS}
              />
            </StageConfigField>
          </>
        )}
        {modes.includes(SelectorMode.Label) && selectedMode === SelectorMode.Label && (
          <>
            <StageConfigField label="Kinds">
              <Select
                clearable={false}
                multi={true}
                value={selectedKinds}
                options={kinds.map((k) => ({ value: k, label: k }))}
                onChange={(options: Array<Option<string>>) => this.handleKindsChange(options.map((o) => o.value))}
              />
            </StageConfigField>
            <StageConfigField label="Labels">
              <LabelEditor
                labelSelectors={get(selector, 'labelSelectors.selectors', [])}
                onLabelSelectorsChange={this.handleLabelSelectorsChange}
              />
            </StageConfigField>
          </>
        )}
      </>
    );
  }
}
