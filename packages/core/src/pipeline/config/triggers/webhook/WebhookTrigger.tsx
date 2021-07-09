import { FormikProps } from 'formik';
import React from 'react';

import { SETTINGS } from '../../../../config/settings';
import { IWebhookTrigger } from '../../../../domain';
import { MapEditorInput } from '../../../../forms';
import { HelpField } from '../../../../help';
import { FormikFormField, TextInput } from '../../../../presentation';

export interface IWebhookTriggerProps {
  formik: FormikProps<IWebhookTrigger>;
}

export function WebhookTrigger(webhookTriggerProps: IWebhookTriggerProps) {
  const { formik } = webhookTriggerProps;
  const trigger = formik.values;
  const { source, type } = trigger;

  return (
    <>
      <FormikFormField
        name="source"
        label="Source"
        help={<HelpField id="pipeline.config.trigger.webhook.source" />}
        input={(props) => (
          <div className="flex-container-v">
            <TextInput {...props} />
            <i>{`${SETTINGS.gateUrl}/webhooks/${type}/${source || '<source>'}`}</i>
          </div>
        )}
      />

      <FormikFormField
        name="payloadConstraints"
        label="Payload Constraints"
        help={<HelpField id="pipeline.config.trigger.webhook.payloadConstraints" />}
        input={(props) => <MapEditorInput {...props} addButtonLabel="Add payload constraint" />}
      />
    </>
  );
}
