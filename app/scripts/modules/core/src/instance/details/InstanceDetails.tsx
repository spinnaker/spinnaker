import * as React from 'react';
import { Subject, Observable } from 'rxjs';

import { Overridable, IOverridableProps } from 'core/overrideRegistry';
import { Application } from 'core/application';
import { Spinner } from 'core/widgets';
import { SkinService } from 'core/cloudProvider/skin.service';

export interface IInstanceDetailsProps extends IOverridableProps {
  $stateParams: {
    provider: string;
    instanceId: string;
  };
  app: Application;
}
export interface IInstanceDetailsState {
  accountId: string;
  loading: boolean;
}

export class InstanceDetails extends React.Component<IInstanceDetailsProps, IInstanceDetailsState> {
  public state = {
    accountId: null,
    loading: false,
  } as IInstanceDetailsState;

  private destroy$ = new Subject();
  private props$ = new Subject<IInstanceDetailsProps>();

  public componentDidMount() {
    this.props$
      .do(() => this.setState({ loading: true, accountId: null }))
      .switchMap(({ app, $stateParams }) => {
        const acct = SkinService.getAccountForInstance($stateParams.provider, $stateParams.instanceId, app);
        return Observable.fromPromise(acct);
      })
      .takeUntil(this.destroy$)
      .subscribe((accountId: string) => {
        this.setState({ accountId, loading: false });
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
    const { accountId, loading } = this.state;
    if (loading) {
      return <Spinner />;
    }

    return <InstanceDetailsCmp {...this.props} accountId={accountId} />;
  }
}

@Overridable('instance.details')
export class InstanceDetailsCmp extends React.Component<IInstanceDetailsProps> {
  public render() {
    return <h3>Instance Details</h3>;
  }
}
