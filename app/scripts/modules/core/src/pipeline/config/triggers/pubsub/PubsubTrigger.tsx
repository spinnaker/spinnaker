import { SETTINGS } from 'core/config/settings';
import { IPubsubSubscription, IPubsubTrigger } from 'core/domain';
import { MapEditor } from 'core/forms';
import { HelpField } from 'core/help';

import { BaseTrigger } from 'core/pipeline';
import { FormField, ReactSelectInput } from 'core/presentation';
import { PubsubSubscriptionReader } from 'core/pubsub';
import { Spinner } from 'core/widgets';
import * as React from 'react';

import { Observable, Subject } from 'rxjs';

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
      const systemsOptions = this.pubsubSystems.map(sys => ({ label: sys, value: sys }));
      const subscriptionOptions = filteredPubsubSubscriptions.map(sub => ({ label: sub, value: sub }));

      return (
        <>
          <FormField
            label="Pub/Sub System Type"
            value={trigger.pubsubSystem}
            onChange={e => this.onUpdateTrigger({ pubsubSystem: e.target.value })}
            input={props => (
              <ReactSelectInput
                {...props}
                placeholder="Select Pub/Sub System"
                options={systemsOptions}
                clearable={false}
              />
            )}
          />

          <FormField
            label="Subscription Name"
            value={trigger.subscriptionName}
            onChange={e => this.onUpdateTrigger({ subscriptionName: e.target.value })}
            input={props => (
              <ReactSelectInput
                {...props}
                placeholder="Select Pub/Sub Subssription"
                options={subscriptionOptions}
                clearable={false}
              />
            )}
          />

          <hr />

          <FormField
            label="Payload Constraints"
            help={<HelpField id="pipeline.config.trigger.pubsub.payloadConstraints" />}
            input={() => (
              <MapEditor
                addButtonLabel="Add payload constraint"
                model={p}
                onChange={(payloadConstraints: any) => this.onUpdateTrigger({ payloadConstraints })}
              />
            )}
          />

          <FormField
            label="Attribute Constraints "
            help={<HelpField id="pipeline.config.trigger.pubsub.attributeConstraints" />}
            input={() => (
              <MapEditor
                addButtonLabel="Add attribute constraint"
                model={a}
                onChange={(attributeConstraints: any) => this.onUpdateTrigger({ attributeConstraints })}
              />
            )}
          />
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
