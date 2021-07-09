import { FormikErrors, FormikProps } from 'formik';
import { get, isEqual, partition, uniq } from 'lodash';
import React from 'react';
import VirtualizedSelect from 'react-virtualized-select';
import { combineLatest as observableCombineLatest, Subject } from 'rxjs';
import { distinctUntilChanged, map, mergeMap, switchMap, takeUntil, tap, withLatestFrom } from 'rxjs/operators';

import {
  FirewallLabels,
  InfrastructureCaches,
  ISecurityGroup,
  IWizardPageComponent,
  ReactInjector,
  Spinner,
  timestamp,
} from '@spinnaker/core';
import { AWSProviderSettings } from '../../../aws.settings';
import { IAmazonLoadBalancerUpsertCommand } from '../../../domain';

export interface ISecurityGroupsProps {
  formik: FormikProps<IAmazonLoadBalancerUpsertCommand>;
  isNew?: boolean;
  onLoadingChanged(isLoading: boolean): void;
}

export interface ISecurityGroupsState {
  availableSecurityGroups: Array<{ label: string; value: string }>;
  defaultSecurityGroups: string[];
  loaded: boolean;
  refreshing: boolean;
  removed: string[];
  refreshTime: number;
}

export class SecurityGroups
  extends React.Component<ISecurityGroupsProps, ISecurityGroupsState>
  implements IWizardPageComponent<IAmazonLoadBalancerUpsertCommand> {
  private destroy$ = new Subject<void>();
  private props$ = new Subject<ISecurityGroupsProps>();
  private refresh$ = new Subject<void>();

  constructor(props: ISecurityGroupsProps) {
    super(props);

    const defaultSecurityGroups = get<string[]>(AWSProviderSettings, 'defaultSecurityGroups', []);
    this.state = {
      availableSecurityGroups: [],
      defaultSecurityGroups,
      loaded: false,
      refreshing: false,
      removed: [],
      refreshTime: InfrastructureCaches.get('securityGroups').getStats().ageMax,
    };
  }

  public validate(): FormikErrors<IAmazonLoadBalancerUpsertCommand> {
    const { removed } = this.state;
    if (removed && removed.length) {
      const label = FirewallLabels.get('Firewalls');
      return { securityGroupsRemoved: `${label} removed: ${removed.join(', ')}` };
    }
    return {};
  }

  private clearRemoved = (): void => {
    this.setState({ removed: [] }, () => this.props.formik.validateForm());
  };

  private updateRemovedSecurityGroups(selectedGroups: string[], availableGroups: ISecurityGroup[]): void {
    const { isNew } = this.props;
    const { defaultSecurityGroups, removed } = this.state;

    const getDesiredGroupNames = (): string[] => {
      const desired = selectedGroups.concat(removed).sort();
      const defaults = isNew ? defaultSecurityGroups : [];
      return uniq(defaults.concat(desired));
    };

    const getAvailableSecurityGroup = (name: string) =>
      availableGroups.find((sg) => sg.name === name || sg.id === name);

    // Organize selected security groups into available/not available
    const [available, notAvailable] = partition(getDesiredGroupNames(), (name) => !!getAvailableSecurityGroup(name));

    // Normalize available security groups from [name or id] to name
    const securityGroups = available.map((name) => getAvailableSecurityGroup(name).name);
    if (!isEqual(selectedGroups, securityGroups)) {
      this.props.formik.setFieldValue('securityGroups', securityGroups);
    }
    this.setState({ removed: notAvailable }, () => this.props.formik.validateForm());
  }

  private handleSecurityGroupsChanged = (newValues: Array<{ label: string; value: string }>): void => {
    this.props.formik.setFieldValue(
      'securityGroups',
      newValues.map((sg) => sg.value),
    );
  };

  private onRefreshStart() {
    this.props.onLoadingChanged(true);
    this.setState({ refreshing: true });
  }

  private onRefreshComplete() {
    this.props.onLoadingChanged(false);
    const refreshTime = InfrastructureCaches.get('securityGroups').getStats().ageMax;
    this.setState({ refreshing: false, loaded: true, refreshTime });
  }

  public componentDidMount(): void {
    const allSecurityGroups$ = this.refresh$.pipe(
      tap(() => this.onRefreshStart()),
      switchMap(() => ReactInjector.cacheInitializer.refreshCache('securityGroups')),
      mergeMap(() => ReactInjector.securityGroupReader.getAllSecurityGroups()),
      tap(() => this.onRefreshComplete()),
    );

    const formValues$ = this.props$.pipe(map((props) => props.formik.values));
    const vpcId$ = formValues$.pipe(
      map((values) => values.vpcId),
      distinctUntilChanged(),
    );

    const availableSecurityGroups$ = observableCombineLatest([vpcId$, allSecurityGroups$]).pipe(
      withLatestFrom(formValues$),
      map(([[vpcId, allSecurityGroups], formValues]) => {
        const forAccount = allSecurityGroups[formValues.credentials] || {};
        const forRegion = (forAccount.aws && forAccount.aws[formValues.region]) || [];
        return forRegion.filter((securityGroup) => vpcId === securityGroup.vpcId).sort();
      }),
    );

    availableSecurityGroups$
      .pipe(withLatestFrom(formValues$), takeUntil(this.destroy$))
      .subscribe(([availableSecurityGroups, formValues]) => {
        const makeOption = (sg: ISecurityGroup) => ({ label: `${sg.name} (${sg.id})`, value: sg.name });
        this.setState({ availableSecurityGroups: availableSecurityGroups.map(makeOption) });
        this.updateRemovedSecurityGroups(formValues.securityGroups, availableSecurityGroups);
      });

    this.refresh$.next();
  }

  public componentDidUpdate(): void {
    this.props$.next(this.props);
  }

  public componentWillUnmount() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public render() {
    const { securityGroups } = this.props.formik.values;
    const { availableSecurityGroups, loaded, refreshing, removed, refreshTime } = this.state;

    return (
      <div className="container-fluid form-horizontal">
        <div>
          {removed.length > 0 && (
            <div className="form-group">
              <div className="col-md-12">
                <div className="alert alert-warning">
                  <p>
                    <i className="fa fa-exclamation-triangle" />
                    The following {FirewallLabels.get('firewalls')} could not be found in the selected
                    account/region/VPC and were removed:
                  </p>
                  <ul>
                    {removed.map((sg) => (
                      <li key={sg}>{sg}</li>
                    ))}
                  </ul>
                  <p className="text-right">
                    <a className="btn btn-sm btn-default dirty-flag-dismiss clickable" onClick={this.clearRemoved}>
                      Okay
                    </a>
                  </p>
                </div>
              </div>
            </div>
          )}
          <div className="form-group">
            <div className="col-md-3 sm-label-right">{FirewallLabels.get('Firewalls')}</div>
            <div className="col-md-9">
              {!loaded && (
                <div style={{ paddingTop: '13px' }}>
                  <Spinner size="small" />
                </div>
              )}
              {loaded && (
                <VirtualizedSelect
                  // className=""
                  multi={true}
                  value={securityGroups}
                  options={availableSecurityGroups}
                  onChange={this.handleSecurityGroupsChanged}
                  clearable={false}
                />
              )}
            </div>
          </div>

          <div className="form-group small" style={{ marginTop: '20px' }}>
            <div className="col-md-9 col-md-offset-3">
              <p>
                {refreshing && (
                  <span>
                    <span className="fa fa-sync-alt fa-spin" />{' '}
                  </span>
                )}
                {FirewallLabels.get('Firewalls')}
                {!refreshing && <span> last refreshed {timestamp(refreshTime)}</span>}
                {refreshing && <span> refreshing...</span>}
              </p>
              <p>
                If you're not finding a {FirewallLabels.get('firewall')} that was recently added,{' '}
                <a className="clickable" onClick={() => this.refresh$.next()}>
                  click here
                </a>{' '}
                to refresh the list.
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
