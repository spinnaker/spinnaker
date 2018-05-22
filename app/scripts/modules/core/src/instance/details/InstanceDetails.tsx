import * as React from 'react';
import { Subject, Observable } from 'rxjs';

import { Overridable, IOverridableProps } from 'core/overrideRegistry';
import { Application } from 'core/application';
import { AccountService } from 'core/account/AccountService';
import { Spinner } from 'core/widgets';
import { SkinService } from 'core/cloudProvider/skin.service';
import { IMoniker } from 'core/naming';

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
  moniker: IMoniker;
  environment: string;
  loading: boolean;
}

export class InstanceDetails extends React.Component<IInstanceDetailsProps, IInstanceDetailsState> {
  public state = {
    accountId: null,
    moniker: null,
    environment: null,
    loading: false,
  } as IInstanceDetailsState;

  private destroy$ = new Subject();
  private props$ = new Subject<IInstanceDetailsProps>();

  public componentDidMount() {
    this.props$
      .do(() => this.setState({ loading: true, accountId: null, moniker: null, environment: null }))
      .switchMap(({ app, $stateParams }) => {
        const accountId = SkinService.getAccountForInstance($stateParams.provider, $stateParams.instanceId, app);
        return Observable.fromPromise(
          Promise.all([
            accountId,
            SkinService.getMonikerForInstance($stateParams.provider, $stateParams.instanceId, app),
            accountId.then(id => AccountService.getAccountDetails(id)),
          ]),
        );
      })
      .takeUntil(this.destroy$)
      .subscribe(([accountId, moniker, { environment }]) => {
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
    const { accountId, moniker, environment, loading } = this.state;
    if (loading) {
      return <Spinner />;
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
