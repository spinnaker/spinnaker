import * as React from 'react';
import { Subject, Observable } from 'rxjs';

import { Overridable, IOverridableProps } from 'core/overrideRegistry';
import { Application } from 'core/application';
import { AccountService } from 'core/account/AccountService';
import { Spinner } from 'core/widgets';
import { SkinService } from 'core/cloudProvider/skin.service';
import { IMoniker } from 'core/naming';
import { InstanceDetailsPane } from './InstanceDetailsPane';

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
      .do(({ $stateParams: { provider, instanceId } }) => {
        this.setState({ provider, instanceId, loading: true, accountId: null, moniker: null, environment: null });
      })
      .switchMap(({ app, $stateParams }) => {
        const { provider, instanceId } = $stateParams;
        const accountId = Observable.fromPromise(SkinService.getAccountForInstance(provider, instanceId, app));
        const moniker = Observable.fromPromise(SkinService.getMonikerForInstance(provider, instanceId, app));
        const accountDetails = accountId.mergeMap(id => AccountService.getAccountDetails(id));
        return Observable.forkJoin(accountId, moniker, accountDetails);
      })
      .takeUntil(this.destroy$)
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
