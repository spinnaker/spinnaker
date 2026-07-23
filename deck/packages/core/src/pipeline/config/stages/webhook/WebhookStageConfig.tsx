import React from 'react';
import { Modal } from 'react-bootstrap';

import type { IStageConfigProps } from '../common';
import { StageConfigField } from '../common';
import { HelpField } from '../../../../help';
import { ModalClose } from '../../../../modal';
import type { IModalComponentProps } from '../../../../presentation';
import { ReactModal } from '../../../../presentation';
import { JsonUtils } from '../../../../utils';

export interface IWebhookStageViewState {
  waitForCompletion?: boolean;
  statusUrlResolution: string;
  failFastStatusCodes: string;
  retryStatusCodes: string;
  signalCancellation?: boolean;
}

export interface IWebhookStageCommand {
  errorMessage?: string;
  invalid?: boolean;
  payloadJSON: string;
}

export interface ICustomHeader {
  key: string;
  value: string;
}

export interface IWebhookParameter {
  name: string;
  label: string;
  description?: string;
  type: string;
  defaultValue?: string;
}

export interface IPreconfiguredWebhook {
  type: string;
  label: string;
  noUserConfigurableFields: boolean;
  description?: string;
  waitForCompletion?: boolean;
  preconfiguredProperties?: string[];
  parameters?: IWebhookParameter[];
}

interface IWebhookStageConfiguration {
  preconfiguredProperties?: string[];
  waitForCompletion?: boolean;
  noUserConfigurableFields?: boolean;
  parameters?: IWebhookParameter[];
}

interface IWebhookFormState {
  cancelCommand: IWebhookStageCommand;
  command: IWebhookStageCommand;
  failFastStatusCodes: string;
  retryStatusCodes: string;
  signalCancellation: boolean;
  statusUrlResolution: string;
  waitForCompletion: boolean;
}

const METHODS = ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'];

function makePayloadCommand(payload: any): IWebhookStageCommand {
  return { payloadJSON: JsonUtils.makeSortedStringFromObject(payload || {}) };
}

function makeInitialState(stage: any, configuration: IWebhookStageConfiguration): IWebhookFormState {
  return {
    cancelCommand: makePayloadCommand(stage.cancelPayload || {}),
    command: makePayloadCommand(stage.payload || {}),
    failFastStatusCodes: stage.failFastStatusCodes ? stage.failFastStatusCodes.join() : '',
    retryStatusCodes: stage.retryStatusCodes ? stage.retryStatusCodes.join() : '',
    signalCancellation: false,
    statusUrlResolution: stage.statusUrlResolution || 'getMethod',
    waitForCompletion: configuration.waitForCompletion || stage.waitForCompletion || false,
  };
}

function parseStatusCodes(statusCodes: string): number[] {
  return statusCodes
    .split(',')
    .map((statusCode) => statusCode.trim())
    .map((statusCode) => parseInt(statusCode, 10))
    .filter((statusCode) => !isNaN(statusCode));
}

function parsePayload(command: IWebhookStageCommand): { command: IWebhookStageCommand; payload: any } {
  try {
    return {
      command: { ...command, errorMessage: '', invalid: false },
      payload: command.payloadJSON ? JSON.parse(command.payloadJSON) : null,
    };
  } catch (error) {
    return {
      command: { ...command, errorMessage: error.message, invalid: true },
      payload: undefined,
    };
  }
}

class AddCustomHeaderModal extends React.Component<IModalComponentProps<ICustomHeader>, ICustomHeader> {
  public state: ICustomHeader = { key: '', value: '' };

  private submit = (event: React.FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const key = this.state.key.trim();
    if (key) {
      this.props.closeModal?.({ key, value: this.state.value });
    }
  };

  public render() {
    const dismissModal = this.props.dismissModal || (() => {});
    return (
      <form role="form" className="container-fluid" noValidate onSubmit={this.submit}>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Add Custom Header</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="form-group row">
            <div className="col-sm-3 sm-label-right">Key</div>
            <div className="col-sm-9">
              <input
                className="form-control input-sm"
                onChange={(event) => this.setState({ key: event.target.value })}
                required
                type="text"
                value={this.state.key}
              />
            </div>
          </div>
          <div className="form-group row">
            <div className="col-sm-3 sm-label-right">Value</div>
            <div className="col-sm-9">
              <input
                className="form-control input-sm"
                onChange={(event) => this.setState({ value: event.target.value })}
                required
                type="text"
                value={this.state.value}
              />
            </div>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <button type="button" className="btn btn-default" onClick={dismissModal}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={!this.state.key.trim()}>
            <span className="far fa-check-circle" /> Add
          </button>
        </Modal.Footer>
      </form>
    );
  }
}

export function WebhookStageConfig({
  configuration = {},
  stage,
  stageFieldUpdated,
  updateStageField,
}: IStageConfigProps) {
  const webhookConfiguration = configuration as IWebhookStageConfiguration;
  const preconfiguredProperties = webhookConfiguration.preconfiguredProperties || [];
  const parameters = webhookConfiguration.parameters || [];
  const noUserConfigurableFields = webhookConfiguration.noUserConfigurableFields;
  const [formState, setFormState] = React.useState<IWebhookFormState>(() =>
    makeInitialState(stage, webhookConfiguration),
  );

  React.useEffect(() => {
    setFormState(makeInitialState(stage, webhookConfiguration));

    const defaults: any = {};
    const statusUrlResolution = stage.statusUrlResolution || 'getMethod';
    if (stage.statusUrlResolution !== statusUrlResolution) {
      defaults.statusUrlResolution = statusUrlResolution;
    }
    if (webhookConfiguration.waitForCompletion && stage.waitForCompletion !== webhookConfiguration.waitForCompletion) {
      defaults.waitForCompletion = webhookConfiguration.waitForCompletion;
    }
    if (parameters.length) {
      const parameterValues = { ...(stage.parameterValues || {}) };
      let changed = !stage.parameterValues;
      parameters.forEach((parameter) => {
        if (!(parameter.name in parameterValues) && parameter.defaultValue !== null) {
          parameterValues[parameter.name] = parameter.defaultValue;
          changed = true;
        }
      });
      if (changed) {
        defaults.parameterValues = parameterValues;
      }
    }
    if (Object.keys(defaults).length) {
      updateStageField(defaults);
    }
  }, [stage.refId]);

  const displayField = (field: string): boolean => !preconfiguredProperties.some((property) => property === field);

  const updateTextField = (field: string) => (event: React.ChangeEvent<HTMLInputElement>) => {
    updateStageField({ [field]: event.target.value });
  };

  const updatePayload = (payloadJSON: string) => {
    const result = parsePayload({ ...formState.command, payloadJSON });
    setFormState((current) => ({ ...current, command: result.command }));
    if (result.payload !== undefined) {
      updateStageField({ payload: result.payload });
    }
  };

  const updateCancelPayload = (payloadJSON: string) => {
    const result = parsePayload({ ...formState.cancelCommand, payloadJSON });
    setFormState((current) => ({ ...current, cancelCommand: result.command }));
    if (result.payload !== undefined) {
      updateStageField({ cancelPayload: result.payload });
    }
  };

  const updateFailFastCodes = (failFastStatusCodes: string) => {
    setFormState((current) => ({ ...current, failFastStatusCodes }));
    updateStageField({ failFastStatusCodes: parseStatusCodes(failFastStatusCodes) });
  };

  const updateRetryCodes = (retryStatusCodes: string) => {
    setFormState((current) => ({ ...current, retryStatusCodes }));
    updateStageField({ retryStatusCodes: parseStatusCodes(retryStatusCodes) });
  };

  const updateParameterValue = (parameterName: string, value: string) => {
    updateStageField({ parameterValues: { ...(stage.parameterValues || {}), [parameterName]: value } });
  };

  const updateCustomHeader = (key: string, value: string) => {
    updateStageField({ customHeaders: { ...(stage.customHeaders || {}), [key]: value } });
  };

  const removeCustomHeader = (key: string) => {
    const customHeaders = { ...(stage.customHeaders || {}) };
    delete customHeaders[key];
    updateStageField({ customHeaders });
  };

  const addCustomHeader = () => {
    ReactModal.show(AddCustomHeaderModal, {}, { dialogClassName: 'modal-md' })
      .then((customHeader) => updateCustomHeader(customHeader.key, customHeader.value))
      .catch(() => {});
  };

  const updateWaitForCompletion = (waitForCompletion: boolean) => {
    setFormState((current) => ({ ...current, waitForCompletion }));
    updateStageField({ waitForCompletion });
  };

  const updateStatusUrlResolution = (statusUrlResolution: string) => {
    setFormState((current) => ({ ...current, statusUrlResolution }));
    updateStageField({ statusUrlResolution });
  };

  const updateSignalCancellation = (signalCancellation: boolean) => {
    setFormState((current) => ({ ...current, signalCancellation }));
    if (signalCancellation) {
      updateStageField({ cancelMethod: 'POST' });
      return;
    }

    delete stage.cancelEndpoint;
    delete stage.cancelMethod;
    delete stage.cancelPayload;
    setFormState((current) => ({ ...current, cancelCommand: makePayloadCommand(stage.cancelPayload || {}) }));
    stageFieldUpdated();
  };

  const customHeaders = stage.customHeaders || {};
  const hasCustomHeaders = Object.keys(customHeaders).length > 0;

  return (
    <div className="form-horizontal">
      {preconfiguredProperties.length > 0 && (
        <div className="alert alert-info">
          <strong>Note:</strong> {noUserConfigurableFields ? 'All' : 'Some'} of the settings of this stage are
          preconfigured, and cannot be changed.
        </div>
      )}

      {displayField('url') && (
        <StageConfigField label="Webhook URL">
          <input
            className="form-control input-sm"
            onChange={updateTextField('url')}
            type="text"
            value={stage.url || ''}
          />
        </StageConfigField>
      )}

      {displayField('method') && (
        <StageConfigField label="Method">
          <select
            className="form-control input-sm"
            onChange={(event) => updateStageField({ method: event.target.value })}
            value={stage.method || ''}
          >
            <option value="">Select a method...</option>
            {METHODS.map((method) => (
              <option key={method} value={method}>
                {method}
              </option>
            ))}
          </select>
        </StageConfigField>
      )}

      {displayField('failFastStatusCodes') && (
        <StageConfigField label="Fail Fast HTTP Statuses" helpKey="pipeline.config.webhook.failFastCodes">
          <input
            className="form-control input-sm"
            onChange={(event) => updateFailFastCodes(event.target.value)}
            type="text"
            value={formState.failFastStatusCodes}
          />
        </StageConfigField>
      )}

      {parameters.length > 0 && (
        <StageConfigField label="Parameters">
          {parameters
            .slice()
            .sort((a, b) => (a as any).order - (b as any).order || a.name.localeCompare(b.name))
            .map((parameter) => (
              <div className="form-group" key={parameter.name}>
                <div className="col-md-4 sm-label-right">
                  <b className="break-word">{parameter.label}</b>
                  {parameter.description && <HelpField content={parameter.description} />}
                </div>
                <div className="col-md-8">
                  <input
                    className="form-control input-sm"
                    onChange={(event) => updateParameterValue(parameter.name, event.target.value)}
                    type="text"
                    value={(stage.parameterValues || {})[parameter.name] || ''}
                  />
                </div>
              </div>
            ))}
        </StageConfigField>
      )}

      {stage.method !== 'GET' && stage.method !== 'HEAD' && displayField('payload') && (
        <StageConfigField label="Payload" helpKey="pipeline.config.webhook.payload">
          <textarea
            className="code form-control flex-fill"
            onChange={(event) => updatePayload(event.target.value)}
            rows={5}
            value={formState.command.payloadJSON}
          />
          {formState.command.invalid && <div className="error-message">Error: {formState.command.errorMessage}</div>}
        </StageConfigField>
      )}

      {displayField('customHeaders') && (
        <StageConfigField label="Custom Headers" helpKey="pipeline.config.webhook.customHeaders">
          {hasCustomHeaders && (
            <table className="table table-condensed packed">
              <thead>
                <tr>
                  <th style={{ width: '40%' }}>Key</th>
                  <th style={{ width: '60%' }}>Value</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(customHeaders).map(([key, value]) => (
                  <tr key={key}>
                    <td>
                      <strong className="small">{key}</strong>
                    </td>
                    <td>
                      <input
                        className="form-control input-sm"
                        onChange={(event) => updateCustomHeader(key, event.target.value)}
                        required
                        type="text"
                        value={String(value)}
                      />
                    </td>
                    <td className="text-right">
                      <a className="small" onClick={() => removeCustomHeader(key)}>
                        Remove
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <button className="btn btn-block btn-sm add-new" onClick={addCustomHeader} type="button">
            <span className="glyphicon glyphicon-plus-sign" /> Add Custom Header
          </button>
        </StageConfigField>
      )}

      {displayField('waitForCompletion') && (
        <div className="form-group">
          <div className="col-md-8 col-md-offset-1">
            <div className="checkbox pull-left">
              <label>
                <input
                  checked={formState.waitForCompletion}
                  name="waitForCompletion"
                  onChange={(event) => updateWaitForCompletion(event.target.checked)}
                  type="checkbox"
                />{' '}
                <strong>Wait for completion</strong>
                <HelpField id="pipeline.config.webhook.waitForCompletion" />
              </label>
            </div>
          </div>
        </div>
      )}

      {formState.waitForCompletion && (
        <>
          {displayField('statusUrlResolution') && (
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Status URL</div>
              <div className="col-md-9 radio">
                <label>
                  <input
                    checked={formState.statusUrlResolution === 'getMethod'}
                    id="statusUrlResolutionIsGetMethod"
                    name="statusUrlResolution"
                    onChange={() => updateStatusUrlResolution('getMethod')}
                    type="radio"
                  />{' '}
                  GET method against webhook URL
                  <HelpField id="pipeline.config.webhook.statusUrlResolutionIsGetMethod" />
                </label>
              </div>
              <div className="col-md-9 col-md-offset-3 radio">
                <label>
                  <input
                    checked={formState.statusUrlResolution === 'locationHeader'}
                    id="statusUrlResolutionIsLocationHeader"
                    name="statusUrlResolution"
                    onChange={() => updateStatusUrlResolution('locationHeader')}
                    type="radio"
                  />{' '}
                  From the Location header
                  <HelpField id="pipeline.config.webhook.statusUrlResolutionIsLocationHeader" />
                </label>
              </div>
              <div className="col-md-9 col-md-offset-3 radio">
                <label>
                  <input
                    checked={formState.statusUrlResolution === 'webhookResponse'}
                    id="statusUrlResolutionIsWebhookResponse"
                    name="statusUrlResolution"
                    onChange={() => updateStatusUrlResolution('webhookResponse')}
                    type="radio"
                  />{' '}
                  From webhook's response
                  <HelpField id="pipeline.config.webhook.useStatusUrlFromLocationHeaderFalse" />
                </label>
              </div>
            </div>
          )}

          {formState.statusUrlResolution === 'webhookResponse' && displayField('statusUrlJsonPath') && (
            <StageConfigField label="Status URL path" helpKey="pipeline.config.webhook.statusUrlJsonPath">
              <input
                className="form-control input-sm"
                onChange={updateTextField('statusUrlJsonPath')}
                required
                type="text"
                value={stage.statusUrlJsonPath || ''}
              />
            </StageConfigField>
          )}

          {displayField('waitBeforeMonitor') && (
            <StageConfigField label="Delay before monitoring" helpKey="pipeline.config.webhook.waitBeforeMonitor">
              <input
                className="form-control input-sm"
                onChange={updateTextField('waitBeforeMonitor')}
                type="text"
                value={stage.waitBeforeMonitor || ''}
              />
            </StageConfigField>
          )}

          {displayField('retryStatusCodes') && (
            <StageConfigField label="Retry HTTP Statuses" helpKey="pipeline.config.webhook.retryStatusCodes">
              <input
                className="form-control input-sm"
                onChange={(event) => updateRetryCodes(event.target.value)}
                type="text"
                value={formState.retryStatusCodes}
              />
            </StageConfigField>
          )}

          {displayField('statusJsonPath') && (
            <StageConfigField label="Status JsonPath" helpKey="pipeline.config.webhook.statusJsonPath">
              <input
                className="form-control input-sm"
                onChange={updateTextField('statusJsonPath')}
                type="text"
                value={stage.statusJsonPath || ''}
              />
            </StageConfigField>
          )}

          {displayField('progressJsonPath') && (
            <StageConfigField label="Progress location" helpKey="pipeline.config.webhook.progressJsonPath">
              <input
                className="form-control input-sm"
                onChange={updateTextField('progressJsonPath')}
                type="text"
                value={stage.progressJsonPath || ''}
              />
            </StageConfigField>
          )}

          {displayField('successStatuses') && (
            <StageConfigField label="SUCCESS status mapping" helpKey="pipeline.config.webhook.successStatuses">
              <input
                className="form-control input-sm"
                onChange={updateTextField('successStatuses')}
                type="text"
                value={stage.successStatuses || ''}
              />
            </StageConfigField>
          )}

          {displayField('canceledStatuses') && (
            <StageConfigField label="CANCELED status mapping" helpKey="pipeline.config.webhook.canceledStatuses">
              <input
                className="form-control input-sm"
                onChange={updateTextField('canceledStatuses')}
                type="text"
                value={stage.canceledStatuses || ''}
              />
            </StageConfigField>
          )}

          {displayField('terminalStatuses') && (
            <StageConfigField label="TERMINAL status mapping" helpKey="pipeline.config.webhook.terminalStatuses">
              <input
                className="form-control input-sm"
                onChange={updateTextField('terminalStatuses')}
                type="text"
                value={stage.terminalStatuses || ''}
              />
            </StageConfigField>
          )}

          {displayField('signalCancellation') && (
            <div className="form-group">
              <div className="col-md-8 col-md-offset-1">
                <div className="checkbox pull-left">
                  <label>
                    <input
                      checked={formState.signalCancellation}
                      name="signalCancellation"
                      onChange={(event) => updateSignalCancellation(event.target.checked)}
                      type="checkbox"
                    />{' '}
                    <strong>Signal on cancellation</strong>
                    <HelpField id="pipeline.config.webhook.signalCancellation" />
                  </label>
                </div>
              </div>
            </div>
          )}

          {formState.signalCancellation && (
            <>
              {displayField('cancelEndpoint') && (
                <StageConfigField label="Cancellation URL">
                  <input
                    className="form-control input-sm"
                    onChange={updateTextField('cancelEndpoint')}
                    type="text"
                    value={stage.cancelEndpoint || ''}
                  />
                </StageConfigField>
              )}

              {displayField('cancelMethod') && (
                <StageConfigField label="Method">
                  <select
                    className="form-control input-sm"
                    onChange={(event) => updateStageField({ cancelMethod: event.target.value })}
                    value={stage.cancelMethod || ''}
                  >
                    <option value="">Select a method...</option>
                    {METHODS.map((method) => (
                      <option key={method} value={method}>
                        {method}
                      </option>
                    ))}
                  </select>
                </StageConfigField>
              )}

              {stage.cancelMethod !== 'GET' && stage.cancelMethod !== 'HEAD' && displayField('cancelPayload') && (
                <StageConfigField label="Cancellation payload" helpKey="pipeline.config.webhook.cancelPayload">
                  <textarea
                    className="code form-control flex-fill"
                    onChange={(event) => updateCancelPayload(event.target.value)}
                    rows={5}
                    value={formState.cancelCommand.payloadJSON}
                  />
                  {formState.cancelCommand.invalid && (
                    <div className="error-message">Error: {formState.cancelCommand.errorMessage}</div>
                  )}
                </StageConfigField>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}
