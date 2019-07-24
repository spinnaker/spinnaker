import * as React from 'react';

import { HelpField } from 'core/help';
import { MapEditor } from 'core/forms';
import { IWebhookTrigger } from 'core/domain';
import { SETTINGS } from 'core/config/settings';
import { FormField, TextInput } from 'core/presentation';

export interface IWebhookTriggerProps {
  trigger: IWebhookTrigger;
  triggerUpdated: (trigger: IWebhookTrigger) => void;
}

export class WebhookTrigger extends React.Component<IWebhookTriggerProps> {
  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  public render() {
    const { trigger } = this.props;
    const { source, type } = trigger;
    const p = trigger.payloadConstraints || {};
    return (
      <>
        <FormField
          label="Source"
          help={<HelpField id="pipeline.config.trigger.webhook.source" />}
          value={source}
          onChange={e => this.onUpdateTrigger({ source: e.target.value })}
          input={props => (
            <>
              <TextInput {...props} />
              <i>{`${SETTINGS.gateUrl}/webhooks/${type}/${source || '<source>'}`}</i>
            </>
          )}
        />

        <FormField
          label="Payload Constraints"
          help={<HelpField id="pipeline.config.trigger.webhook.payloadConstraints" />}
          input={() => (
            <MapEditor
              addButtonLabel="Add payload constraint"
              model={p}
              onChange={(payloadConstraints: any) => this.onUpdateTrigger({ payloadConstraints })}
            />
          )}
        />
      </>
    );
  }
}
