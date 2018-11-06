import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { Formik, Field, Form, FormikErrors } from 'formik';

import { ModalClose, ReactModal, SubmitButton, noop } from '@spinnaker/core';

import { IAuthenticateOidcActionConfig } from 'amazon/loadBalancer/OidcConfigReader';

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

  private close = (): void => {
    this.props.dismissModal.apply(null, arguments);
  };

  private submit = (data: IAuthenticateOidcActionConfig): void => {
    this.props.closeModal(data);
  };

  private validate = (values: IAuthenticateOidcActionConfig): Partial<FormikErrors<IAuthenticateOidcActionConfig>> => {
    const errors: Partial<FormikErrors<IAuthenticateOidcActionConfig>> = {};
    if (!values.authorizationEndpoint) {
      errors.authorizationEndpoint = 'You must provide an authorization endpoint.';
    }
    if (!values.clientId) {
      errors.clientId = 'You must provide a client id.';
    }
    if (!values.issuer) {
      errors.issuer = 'You must provide an issuer.';
    }
    if (!values.scope) {
      errors.scope = 'You must provide a scope.';
    }
    if (!values.sessionCookieName) {
      errors.sessionCookieName = 'You must provide a sessionCookieName.';
    }
    if (!values.tokenEndpoint) {
      errors.tokenEndpoint = 'You must provide a token endpoint.';
    }
    if (!values.userInfoEndpoint) {
      errors.userInfoEndpoint = 'You must provide a user info endpoint.';
    }
    return errors;
  };

  public render() {
    const { initialValues } = this.state;

    const submitLabel = 'Save Client';

    return (
      <div>
        <Formik<IAuthenticateOidcActionConfig>
          initialValues={initialValues}
          onSubmit={this.submit}
          validate={this.validate}
          render={({ isValid }) => (
            <Form className="form-horizontal">
              <ModalClose dismiss={this.close} />
              <Modal.Header>
                <h3>Configure OIDC Client</h3>
              </Modal.Header>
              <Modal.Body>
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">Issuer</div>
                  <div className="col-sm-9">
                    <Field
                      className="form-control input-sm"
                      name="issuer"
                      placeholder="Enter the OpenID Provider"
                      required={true}
                    />
                  </div>
                </div>
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">Authorization Endpoint</div>
                  <div className="col-sm-9">
                    <Field
                      className="form-control input-sm"
                      name="authorizationEndpoint"
                      placeholder="Enter OpenID provider server endpoint"
                      required={true}
                    />
                  </div>
                </div>
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">Token Endpoint</div>
                  <div className="col-sm-9">
                    <Field
                      className="form-control input-sm"
                      name="tokenEndpoint"
                      placeholder="Enter a URI for your token endpoint"
                      required={true}
                    />
                  </div>
                </div>
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">User info Endpoint</div>
                  <div className="col-sm-9">
                    <Field
                      className="form-control input-sm"
                      name="userInfoEndpoint"
                      placeholder="Enter a URI for your user info endpoint"
                      required={true}
                    />
                  </div>
                </div>
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">Client ID</div>
                  <div className="col-sm-9">
                    <Field
                      className="form-control input-sm"
                      name="clientId"
                      placeholder="Enter the client ID"
                      required={true}
                    />
                  </div>
                </div>
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">Client secret</div>
                  <div className="col-sm-9">
                    <Field
                      className="form-control input-sm"
                      name="clientSecret"
                      placeholder="Enter the client secret"
                      required={true}
                    />
                  </div>
                </div>
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
