import * as React from 'react';

import { Observable, Subject } from 'rxjs';

import { ITrigger } from 'core/domain';
import { SETTINGS } from 'core/config/settings';
import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { RunAsUser } from 'core/pipeline';

export interface IBaseTriggerConfigProps {
  triggerContents: React.ReactNode;
  trigger: ITrigger;
  triggerUpdated?: (trigger: ITrigger) => void;
}

export interface IBaseTriggerState {
  serviceAccounts?: string[];
}

export class BaseTrigger extends React.Component<IBaseTriggerConfigProps, IBaseTriggerState> {
  private destroy$ = new Subject();

  constructor(props: IBaseTriggerConfigProps) {
    super(props);
    this.state = {
      serviceAccounts: [],
    };
  }

  public componentDidMount(): void {
    if (SETTINGS.feature.fiatEnabled) {
      Observable.fromPromise(ServiceAccountReader.getServiceAccounts())
        .takeUntil(this.destroy$)
        .subscribe(serviceAccounts => this.setState({ serviceAccounts }));
    }
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  private renderRunAsUser = (): React.ReactNode => {
    const { trigger } = this.props;
    const { serviceAccounts } = this.state;
    return (
      <>
        {SETTINGS.feature.fiatEnabled && serviceAccounts && !SETTINGS.feature.managedServiceAccounts && (
          <div className="form-group">
            <RunAsUser
              serviceAccounts={serviceAccounts}
              onChange={(user: string) => this.onUpdateTrigger({ user })}
              value={trigger.user}
              selectColumns={6}
            />
          </div>
        )}
      </>
    );
  };

  public render() {
    const { triggerContents } = this.props;
    const { renderRunAsUser } = this;
    return (
      <>
        {triggerContents}
        {renderRunAsUser()}
      </>
    );
  }
}
