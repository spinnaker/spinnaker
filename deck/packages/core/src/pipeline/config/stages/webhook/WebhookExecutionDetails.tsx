import React from 'react';

import { ExecutionArtifactTab } from '../../../../artifact/react/ExecutionArtifactTab';
import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection, ExecutionDetailsTasks } from '../common';
import { StageFailureMessage } from '../../../details';
import { CopyToClipboard } from '../../../../utils';

const stringify = (value: any) => (typeof value === 'object' ? JSON.stringify(value, null, 2) : value);
const urlPattern = /(https?:\/\/[^\s]+)/g;

function renderLinkedText(text: string): React.ReactNode {
  return (text || '').split(urlPattern).map((part, index) => {
    if (part.match(urlPattern)) {
      return (
        <a href={part} key={index} target="_blank" rel="noopener noreferrer">
          {part}
        </a>
      );
    }
    return part;
  });
}

function getProgressMessage(stage: any): string {
  const context = stage.context || {};
  const webhook = context.webhook || {};
  return webhook.monitor?.progressMessage || context.buildInfo?.progressMessage;
}

function getFailureMessage(stage: any): string {
  const context = stage.context || {};
  const webhook = context.webhook || {};
  const error = webhook.monitor?.error || webhook.error;
  return error ? `Webhook failed: ${error}` : stage.failureMessage;
}

function getBodyContent(stage: any): string {
  const webhook = stage.context?.webhook || {};
  const body = webhook.monitor?.body || webhook.body;
  if (!body && stage.originalStatus !== 'NOT_STARTED' && stage.originalStatus !== 'RUNNING') {
    return '<NO BODY RETURNED BY SERVER>';
  }
  return stringify(body);
}

function WebhookConfigSection(props: IExecutionDetailsSectionProps) {
  const { stage } = props;
  const payload = JSON.stringify(stage.context?.payload, null, 2);
  const body = getBodyContent(stage);
  const progressMessage = getProgressMessage(stage);
  const statusCode = stage.context?.webhook?.monitor?.statusCodeValue || stage.context?.webhook?.statusCodeValue;

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-12">
          <h5>Webhook Stage Configuration</h5>
          <dl className="dl-narrow dl-horizontal">
            <dt>Url</dt>
            <dd>{stage.context?.url}</dd>
            <dt>
              <CopyToClipboard
                className="copy-to-clipboard copy-to-clipboard-sm"
                text={payload}
                toolTip="Copy payload to clipboard"
              />
              Payload
            </dt>
            <dd>
              <pre className="ng-binding" style={{ background: 'unset', border: 'unset', padding: 0 }}>
                {payload}
              </pre>
            </dd>
          </dl>
          {stage.context?.waitForCompletion && (
            <dl className="dl-narrow dl-horizontal">
              <dt>Status endpoint</dt>
              <dd>{renderLinkedText(stage.context.statusEndpoint)}</dd>
            </dl>
          )}
        </div>
        {stage.context?.parameterValues && (
          <div className="col-md-12">
            <h5>Parameters</h5>
            <dl className="dl-narrow dl-horizontal">
              {Object.entries(stage.context.parameterValues).map(([key, value]) => (
                <React.Fragment key={key}>
                  <dt>{key}</dt>
                  <dd>{String(value)}</dd>
                </React.Fragment>
              ))}
            </dl>
          </div>
        )}
      </div>
      <StageFailureMessage stage={stage} message={getFailureMessage(stage)} />
      {(progressMessage || stage.status || body) && (
        <div className="well alert-info">
          <h4>Results</h4>
          <dl className="dl-narrow dl-horizontal ng-scope">
            <dt>Status</dt>
            <dd className="ng-binding">{stage.status}</dd>
            <dt>Info</dt>
            <dd className="ng-binding webhook-progress-message" style={{ whiteSpace: 'pre-line' }}>
              {renderLinkedText(progressMessage)}
            </dd>
            <dt>Code</dt>
            <dd className="ng-binding">{statusCode}</dd>
            <dt>
              <CopyToClipboard
                className="copy-to-clipboard copy-to-clipboard-sm"
                text={body}
                toolTip="Copy response to clipboard"
              />
              Response
            </dt>
            <dd>
              <pre
                className="ng-binding"
                style={{ background: 'unset', border: 'unset', maxHeight: 400, overflowY: 'auto', padding: 0 }}
              >
                {body}
              </pre>
            </dd>
          </dl>
        </div>
      )}
    </ExecutionDetailsSection>
  );
}

WebhookConfigSection.title = 'webhookConfig';

export const webhookExecutionDetailsSections = [WebhookConfigSection, ExecutionDetailsTasks, ExecutionArtifactTab];
