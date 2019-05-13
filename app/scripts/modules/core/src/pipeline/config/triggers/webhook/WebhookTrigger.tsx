import * as React from 'react';

import { BaseTrigger } from 'core/pipeline';
import { HelpField } from 'core/help';
import { MapEditor } from 'core/forms';
import { IWebhookTrigger } from 'core/domain';
import { SETTINGS } from 'core/config/settings';
import { TextInput } from 'core/presentation';

export interface IWebhookTriggerProps {
  trigger: IWebhookTrigger;
  triggerUpdated: (trigger: IWebhookTrigger) => void;
}

export class WebhookTrigger extends React.Component<IWebhookTriggerProps> {
  constructor(props: IWebhookTriggerProps) {
    super(props);
  }

  private WebhookTriggerContents() {
    const { trigger } = this.props;
    const { source, type } = trigger;
    const p = trigger.payloadConstraints || {};
    return (
      <>
        <dl className="dl-horizontal dl-flex">
          <dd>
            <i>{`${SETTINGS.gateUrl}/webhooks/${type}/${source || '<source>'}`}</i>
          </dd>
        </dl>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <span>Source </span>
            <HelpField id="pipeline.config.trigger.webhook.source" />
          </div>
          <div className="col-md-6">
            <TextInput
              className="form-control input-sm"
              name="source"
              onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                this.onUpdateTrigger({ source: event.target.value })
              }
              value={source}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <span>Payload Constraints </span>
            <HelpField id="pipeline.config.trigger.webhook.payloadConstraints" />
          </div>
          <div className="col-md-6">
            <MapEditor
              addButtonLabel="Add payload constraint"
              model={p}
              onChange={(payloadConstraints: any) => this.onUpdateTrigger({ payloadConstraints })}
            />
          </div>
        </div>
      </>
    );
  }

  private onUpdateTrigger = (update: any) => {
    this.props.triggerUpdated &&
      this.props.triggerUpdated({
        ...this.props.trigger,
        ...update,
      });
  };

  public render() {
    const { WebhookTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<WebhookTriggerContents />} />;
  }
}
