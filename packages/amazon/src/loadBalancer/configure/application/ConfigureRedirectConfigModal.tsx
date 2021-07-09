import { Form } from 'formik';
import { pickBy } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import {
  FormikFormField,
  HelpField,
  ModalClose,
  noop,
  ReactModal,
  ReactSelectInput,
  SelectInput,
  SpinFormik,
  SubmitButton,
  TextInput,
} from '@spinnaker/core';
import { IRedirectActionConfig } from '../../../domain';

import './ConfigureConfigModal.css';

export interface IConfigureRedirectConfigModalProps {
  config: IRedirectActionConfig;
  closeModal?(result?: any): void; // provided by ReactModal
  dismissModal?(rejection?: any): void; // provided by ReactModal
}

export class ConfigureRedirectConfigModal extends React.Component<IConfigureRedirectConfigModalProps> {
  private initialValues: IRedirectActionConfig;

  public static defaultProps: Partial<IConfigureRedirectConfigModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IConfigureRedirectConfigModalProps): Promise<void> {
    return ReactModal.show(ConfigureRedirectConfigModal, props);
  }

  constructor(props: IConfigureRedirectConfigModalProps) {
    super(props);

    const config = props.config || ({} as IRedirectActionConfig);

    this.initialValues = {
      host: config.host || '',
      path: config.path || '',
      port: config.port || '',
      protocol: config.protocol || undefined,
      query: config.query || '',
      statusCode: config.statusCode || 'HTTP_301',
    };
  }

  private close = (reason?: null): void => {
    this.props.dismissModal(reason);
  };

  private submit = (data: IRedirectActionConfig): void => {
    const filteredData: IRedirectActionConfig = pickBy(data, (value: string) => value && value !== '');
    this.props.closeModal(filteredData);
  };

  public render() {
    const submitLabel = 'Save Config';

    return (
      <div className="configure-config-modal">
        <SpinFormik<IRedirectActionConfig>
          initialValues={this.initialValues}
          onSubmit={this.submit}
          render={({ isValid }) => (
            <Form className="form-horizontal">
              <ModalClose dismiss={this.close} />
              <Modal.Header>
                <Modal.Title>
                  Configure Redirect <HelpField id="aws.loadBalancer.redirect" />
                </Modal.Title>
              </Modal.Header>

              <Modal.Body>
                <FormikFormField
                  name="host"
                  label="Host"
                  required={false}
                  input={(props) => <TextInput {...props} />}
                  help={<HelpField id="aws.loadBalancer.redirect.host" />}
                />
                <FormikFormField
                  name="path"
                  label="Path"
                  required={false}
                  input={(props) => <TextInput {...props} />}
                  help={<HelpField id="aws.loadBalancer.redirect.path" />}
                />
                <FormikFormField
                  name="port"
                  label="Port"
                  required={false}
                  input={(props) => <TextInput {...props} />}
                  help={<HelpField id="aws.loadBalancer.redirect.port" />}
                />
                <FormikFormField
                  name="protocol"
                  label="Protocol"
                  required={false}
                  input={(props) => (
                    <ReactSelectInput
                      {...props}
                      stringOptions={['HTTP', 'HTTPS', '#{protocol}']}
                      placeholder="Select Protocol"
                      clearable={false}
                      style={{ width: '130px' }}
                    />
                  )}
                  help={<HelpField id="aws.loadBalancer.redirect.protocol" />}
                />
                <FormikFormField
                  name="query"
                  label="Query"
                  required={false}
                  input={(props) => <TextInput {...props} />}
                  help={<HelpField id="aws.loadBalancer.redirect.query" />}
                />
                <FormikFormField
                  name="statusCode"
                  label="Status Code"
                  required={true}
                  input={(props) => <SelectInput {...props} options={['HTTP_301', 'HTTP_302']} />}
                  help={<HelpField id="aws.loadBalancer.redirect.statusCode" />}
                />
              </Modal.Body>

              <Modal.Footer>
                <button className="btn btn-default" onClick={this.close} type="button">
                  Cancel
                </button>
                <SubmitButton isDisabled={!isValid} submitting={false} isFormSubmit={true} label={submitLabel} />
              </Modal.Footer>
            </Form>
          )}
        />
      </div>
    );
  }
}
