import { FormikProps } from 'formik';
import React from 'react';

import { SETTINGS } from '../../../../config/settings';
import { IPubsubTrigger } from '../../../../domain';
import { MapEditorInput } from '../../../../forms';
import { HelpField } from '../../../../help';
import { FormikFormField, ReactSelectInput, useLatestPromise } from '../../../../presentation';
import { PubsubSubscriptionReader } from '../../../../pubsub';
import { Spinner } from '../../../../widgets';

export interface IPubsubTriggerProps {
  formik: FormikProps<IPubsubTrigger>;
  triggerUpdated: (trigger: IPubsubTrigger) => void;
}

export function PubsubTrigger(pubsubTriggerProps: IPubsubTriggerProps) {
  const { formik } = pubsubTriggerProps;
  const trigger = formik.values;
  const pubsubSystems = SETTINGS.pubsubProviders || ['amazon', 'google'];

  const fetchSubscriptions = useLatestPromise(() => PubsubSubscriptionReader.getPubsubSubscriptions(), []);
  const pubsubSubscriptions = fetchSubscriptions.result || [];
  const subscriptionsLoaded = fetchSubscriptions.status === 'RESOLVED';

  const filteredPubsubSubscriptions = pubsubSubscriptions
    .filter((subscription) => subscription.pubsubSystem === trigger.pubsubSystem)
    .map((subscription) => subscription.subscriptionName);

  if (subscriptionsLoaded) {
    return (
      <>
        <FormikFormField
          name="pubsubSystem"
          label="Pub/Sub System Type"
          input={(props) => (
            <ReactSelectInput {...props} placeholder="Select Pub/Sub System" stringOptions={pubsubSystems} />
          )}
        />

        <FormikFormField
          name="subscriptionName"
          label="Subscription Name"
          input={(props) => (
            <ReactSelectInput
              {...props}
              placeholder="Select Pub/Sub Subscription"
              stringOptions={filteredPubsubSubscriptions}
            />
          )}
        />

        <hr />

        <FormikFormField
          name="payloadConstraints"
          label="Payload Constraints"
          help={<HelpField id="pipeline.config.trigger.pubsub.payloadConstraints" />}
          input={(props) => <MapEditorInput {...props} addButtonLabel="Add payload constraint" />}
        />

        <FormikFormField
          name="attributeConstraints"
          label="Attribute Constraints "
          help={<HelpField id="pipeline.config.trigger.pubsub.attributeConstraints" />}
          input={(props) => <MapEditorInput {...props} addButtonLabel="Add attribute constraint" />}
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
}
