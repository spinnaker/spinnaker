import * as React from 'react';
import Select, { Option } from 'react-select';

import { Observable, Subject } from 'rxjs';

import { BaseTrigger } from 'core/pipeline';
import { HelpField } from 'core/help';
import { IPubsubSubscription, IPubsubTrigger } from 'core/domain';
import { MapEditor } from 'core/forms';
import { PubsubSubscriptionReader } from 'core/pubsub';
import { Spinner } from 'core/widgets';
import { SETTINGS } from 'core/config/settings';

export interface IPubsubTriggerProps {
  trigger: IPubsubTrigger;
  triggerUpdated: (trigger: IPubsubTrigger) => void;
}

export interface IPubsubTriggerState {
  pubsubSubscriptions: IPubsubSubscription[];
  subscriptionsLoaded: boolean;
}

export class PubsubTrigger extends React.Component<IPubsubTriggerProps, IPubsubTriggerState> {
  private destroy$ = new Subject();
  private pubsubSystems = SETTINGS.pubsubProviders || ['google']; // TODO(joonlim): Add amazon once it is confirmed that amazon pub/sub works.

  constructor(props: IPubsubTriggerProps) {
    super(props);
    this.state = {
      pubsubSubscriptions: [],
      subscriptionsLoaded: false,
    };
  }

  public componentDidMount() {
    Observable.fromPromise(PubsubSubscriptionReader.getPubsubSubscriptions())
      .takeUntil(this.destroy$)
      .subscribe(
        pubsubSubscriptions => {
          this.setState({
            pubsubSubscriptions,
            subscriptionsLoaded: true,
          });
        },
        () => {
          this.setState({
            pubsubSubscriptions: [],
            subscriptionsLoaded: true,
          });
        },
      );
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  public PubSubTriggerContents = () => {
    const { pubsubSubscriptions, subscriptionsLoaded } = this.state;
    const { trigger } = this.props;
    const a = trigger.attributeConstraints || {};
    const p = trigger.payloadConstraints || {};
    const filteredPubsubSubscriptions = pubsubSubscriptions
      .filter(subscription => subscription.pubsubSystem === trigger.pubsubSystem)
      .map(subscription => subscription.subscriptionName);

    if (subscriptionsLoaded) {
      return (
        <>
          <div className="form-group">
            <label className="col-md-3 sm-label-right">Pub/Sub System Type</label>
            <div className="col-md-6">
              <Select
                className="form-control input-sm"
                options={this.pubsubSystems.map(sys => ({ label: sys, value: sys }))}
                onChange={(option: Option<string>) => this.onUpdateTrigger({ pubsubSystem: option.value })}
                placeholder="Select Pub/Sub System"
                value={trigger.pubsubSystem}
              />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-3 sm-label-right">Subscription Name</div>
            <div className="col-md-6">
              <Select
                className="form-control input-sm"
                onChange={(option: Option<string>) => this.onUpdateTrigger({ subscriptionName: option.value })}
                options={filteredPubsubSubscriptions.map(sub => ({ label: sub, value: sub }))}
                placeholder="Select Pub/Sub Subscription"
                value={trigger.subscriptionName}
              />
            </div>
          </div>

          <hr />

          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <span>Payload Constraints </span>
              <HelpField id="pipeline.config.trigger.pubsub.payloadConstraints" />
            </div>
            <div className="col-md-9">
              <MapEditor
                addButtonLabel="Add payload constraint"
                model={p}
                onChange={(payloadConstraints: any) => this.onUpdateTrigger({ payloadConstraints })}
              />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <span>Attribute Constraints </span>
              <HelpField id="pipeline.config.trigger.pubsub.attributeConstraints" />
            </div>
            <div className="col-md-9">
              <MapEditor
                addButtonLabel="Add attribute constraint"
                model={a}
                onChange={(attributeConstraints: any) => this.onUpdateTrigger({ attributeConstraints })}
              />
            </div>
          </div>
        </>
      );
    } else {
      return (
        <div className="horizontal middle center" style={{ marginBottom: '250px', height: '150px' }}>
          <Spinner size={'medium'} />
        </div>
      );
    }
  };

  public render() {
    const { PubSubTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<PubSubTriggerContents />} />;
  }
}
