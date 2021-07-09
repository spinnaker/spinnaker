import React from 'react';
import { forkJoin as observableForkJoin, from as observableFrom, Subject } from 'rxjs';
import { mergeMap, switchMap, takeUntil, tap } from 'rxjs/operators';

import { InstanceDetailsPane } from './InstanceDetailsPane';
import { AccountService } from '../../account/AccountService';
import { Application } from '../../application';
import { IMoniker, NameUtils } from '../../naming';
import { IOverridableProps, Overridable } from '../../overrideRegistry';
import { Spinner } from '../../widgets';

export interface IInstanceDetailsProps extends IOverridableProps {
  $stateParams: {
    provider: string;
    instanceId: string;
  };
  app: Application;
  moniker: IMoniker;
  environment: string;
}
export interface IInstanceDetailsState {
  accountId: string;
  environment: string;
  instanceId: string;
  loading: boolean;
  moniker: IMoniker;
  provider: string;
}

export class InstanceDetails extends React.Component<IInstanceDetailsProps, IInstanceDetailsState> {
  public state: IInstanceDetailsState = {
    accountId: null,
    environment: null,
    instanceId: null,
    loading: false,
    moniker: null,
    provider: null,
  };

  private destroy$ = new Subject();
  private props$ = new Subject<IInstanceDetailsProps>();

  public componentDidMount() {
    this.props$
      .pipe(
        tap(({ $stateParams: { provider, instanceId } }) => {
          this.setState({ provider, instanceId, loading: true, accountId: null, moniker: null, environment: null });
        }),
        switchMap(({ app, $stateParams }) => {
          const { provider, instanceId } = $stateParams;
          const accountId = observableFrom(AccountService.getAccountForInstance(provider, instanceId, app));
          const moniker = observableFrom(NameUtils.getMonikerForInstance(provider, instanceId, app));
          const accountDetails = accountId.pipe(mergeMap((id) => AccountService.getAccountDetails(id)));
          return observableForkJoin(accountId, moniker, accountDetails);
        }),
        takeUntil(this.destroy$),
      )
      .subscribe(([accountId, moniker, accountDetails]) => {
        const environment = accountDetails && accountDetails.environment;
        this.setState({ accountId, moniker, environment, loading: false });
      });

    this.props$.next(this.props);
  }

  public componentWillReceiveProps(nextProps: IInstanceDetailsProps) {
    this.props$.next(nextProps);
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public render() {
    const { accountId, instanceId, moniker, environment, loading, provider } = this.state;

    if (loading) {
      return (
        <InstanceDetailsPane>
          <Spinner size="medium" message=" " />
        </InstanceDetailsPane>
      );
    } else if (!accountId || !moniker || !environment) {
      return (
        <InstanceDetailsPane>
          <h4>
            Could not find {provider} instance {instanceId}.
          </h4>
        </InstanceDetailsPane>
      );
    }

    return <InstanceDetailsCmp {...this.props} accountId={accountId} moniker={moniker} environment={environment} />;
  }
}

@Overridable('instance.details')
export class InstanceDetailsCmp extends React.Component<IInstanceDetailsProps> {
  public render() {
    return <h3>Instance Details</h3>;
  }
}
