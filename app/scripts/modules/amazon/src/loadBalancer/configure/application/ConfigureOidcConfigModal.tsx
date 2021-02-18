import { Form, FormikErrors } from 'formik';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { FormikFormField, ModalClose, noop, ReactModal, SpinFormik, SubmitButton, TextInput } from '@spinnaker/core';

import { IAuthenticateOidcActionConfig } from '../../OidcConfigReader';

import './ConfigureConfigModal.css';

export interface IConfigureOidcConfigModalProps {
  config: IAuthenticateOidcActionConfig;
  closeModal?(result?: any): void; // provided by ReactModal
  dismissModal?(rejection?: any): void; // provided by ReactModal
}

export interface IConfigureOidcConfigModalState {
  initialValues: IAuthenticateOidcActionConfig;
}

export class ConfigureOidcConfigModal extends React.Component<
  IConfigureOidcConfigModalProps,
  IConfigureOidcConfigModalState
> {
  public static defaultProps: Partial<IConfigureOidcConfigModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IConfigureOidcConfigModalProps): Promise<void> {
    return ReactModal.show(ConfigureOidcConfigModal, props);
  }

  constructor(props: IConfigureOidcConfigModalProps) {
    super(props);

    const config = props.config || ({} as IAuthenticateOidcActionConfig);

    this.state = {
      initialValues: {
        authorizationEndpoint: config.authorizationEndpoint || '',
        clientId: config.clientId || '',
        clientSecret: config.clientSecret || '',
        issuer: config.issuer || '',
        scope: config.scope || 'openid',
        sessionCookieName: config.sessionCookieName || 'AWSELBAuthSessionCookie',
        tokenEndpoint: config.tokenEndpoint || '',
        userInfoEndpoint: config.userInfoEndpoint || '',
      },
    };
  }

  private close = (reason?: any): void => {
    this.props.dismissModal(reason);
  };

  private submit = (data: IAuthenticateOidcActionConfig): void => {
    this.props.closeModal(data);
  };

  private validate = (): FormikErrors<IAuthenticateOidcActionConfig> => {
    return {};
  };

  public render() {
    const { initialValues } = this.state;

    const submitLabel = 'Save Client';

    return (
      <div className="configure-config-modal">
        <SpinFormik<IAuthenticateOidcActionConfig>
          initialValues={initialValues}
          onSubmit={this.submit}
          validate={this.validate}
          render={({ isValid }) => (
            <Form className="form-horizontal">
              <ModalClose dismiss={this.close} />
              <Modal.Header>
                <Modal.Title>Configure OIDC Client</Modal.Title>
              </Modal.Header>

              <Modal.Body>
                <FormikFormField
                  name="issuer"
                  label="Issuer"
                  required={true}
                  input={(props) => <TextInput {...props} placeholder="Enter the OpenId Provider" />}
                />

                <FormikFormField
                  name="authorizationEndpoint"
                  label="Authorization Endpoint"
                  required={true}
                  input={(props) => <TextInput {...props} placeholder="Enter OpenID provider server endpoint" />}
                />

                <FormikFormField
                  name="tokenEndpoint"
                  label="Token Endpoint"
                  required={true}
                  input={(props) => <TextInput {...props} placeholder="Enter a URI for your token endpoint" />}
                />

                <FormikFormField
                  name="userInfoEndpoint"
                  label="User info Endpoint"
                  required={true}
                  input={(props) => <TextInput {...props} placeholder="Enter a URI for your user info endpoint" />}
                />

                <FormikFormField
                  name="clientId"
                  label="Client ID"
                  required={true}
                  input={(props) => <TextInput {...props} placeholder="Enter the client ID" />}
                />

                <FormikFormField
                  name="clientSecret"
                  label="Client secret"
                  required={true}
                  input={(props) => <TextInput {...props} placeholder="Enter the client secret" />}
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
