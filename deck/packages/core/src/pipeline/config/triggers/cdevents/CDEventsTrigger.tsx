import type { FormikProps } from 'formik';
import React from 'react';

import { SETTINGS } from '../../../../config/settings';
import type { ICDEventsTrigger } from '../../../../domain';
import { MapEditorInput } from '../../../../forms';
import { HelpField } from '../../../../help';
import { FormikFormField, TextInput } from '../../../../presentation';

export interface ICDEventsTriggerProps {
  formik: FormikProps<ICDEventsTrigger>;
}

export function CDEventsTrigger(cdeventsTriggerProps: ICDEventsTriggerProps) {
  const { formik } = cdeventsTriggerProps;
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

      <FormikFormField
        name="attributeConstraints"
        label="Attribute Constraints "
        help={<HelpField id="pipeline.config.trigger.cdevents.attributeConstraints" />}
        input={(props) => <MapEditorInput {...props} addButtonLabel="Add attribute constraint" />}
      />
    </>
  );
}
