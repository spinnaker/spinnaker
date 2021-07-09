import { FormikProps } from 'formik';
import { difference, flatten, get, uniq, uniqBy } from 'lodash';
import React from 'react';

import { Application, IWizardPageComponent, ValidationMessage } from '@spinnaker/core';

import { IAuthenticateOidcActionConfig } from '../../OidcConfigReader';
import {
  IALBListenerCertificate,
  IAmazonCertificate,
  IAmazonNetworkLoadBalancerUpsertCommand,
  IListenerAction,
  IListenerDescription,
  NLBListenerProtocol,
} from '../../../domain';
import { AmazonCertificateReader, AWSProviderSettings } from '../../../index';
import { CertificateSelector } from '../network/CertificateSelectors';

export interface INLBListenersProps {
  app?: Application;
  formik: FormikProps<IAmazonNetworkLoadBalancerUpsertCommand>;
}

export interface INLBListenersState {
  certificates: { [accountId: number]: IAmazonCertificate[] };
  certificateTypes: string[];
  oidcConfigs: IAuthenticateOidcActionConfig[];
}

export interface INLBCertificateSelectorProps {
  availableCertificates: IALBListenerCertificate[];
  certificates: { [accountId: number]: IAmazonCertificate[] };
  formik: FormikProps<IAmazonNetworkLoadBalancerUpsertCommand>;
  app?: Application;
  certificateTypes: string[];
}

export class NLBListeners
  extends React.Component<INLBListenersProps, INLBListenersState>
  implements IWizardPageComponent<IAmazonNetworkLoadBalancerUpsertCommand> {
  public protocols = ['TCP', 'UDP', 'TLS'];
  private removedAuthActions: Map<IListenerDescription, { [key: number]: IListenerAction }> = new Map();

  constructor(props: INLBListenersProps) {
    super(props);
    this.state = {
      certificates: [],
      certificateTypes: get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']),
      oidcConfigs: undefined,
    };
  }

  private getAllTargetGroupsFromListeners(listeners: IListenerDescription[]): string[] {
    const actions = flatten(listeners.map((l) => l.defaultActions));
    const rules = flatten(listeners.map((l) => l.rules));
    actions.push(...flatten(rules.map((r) => r.actions)));
    return uniq(actions.map((a) => a.targetGroupName));
  }

  public validate(values: IAmazonNetworkLoadBalancerUpsertCommand) {
    const errors = {} as any;

    // Check to make sure all target groups have an associated listener
    const targetGroupNames = values.targetGroups.map((tg) => tg.name);
    const usedTargetGroupNames = this.getAllTargetGroupsFromListeners(values.listeners);
    const unusedTargetGroupNames = difference(targetGroupNames, usedTargetGroupNames);
    if (unusedTargetGroupNames.length === 1) {
      errors.listeners = `Target group ${unusedTargetGroupNames[0]} is unused.`;
    } else if (unusedTargetGroupNames.length > 1) {
      errors.listeners = `Target groups ${unusedTargetGroupNames.join(', ')} are unused.`;
    }

    const { listeners } = values;
    if (uniqBy(listeners, 'port').length < listeners.length) {
      errors.listenerPorts = 'Multiple listeners cannot use the same port.';
    }

    return errors;
  }

  private updateListeners(): void {
    this.props.formik.setFieldValue('listeners', this.props.formik.values.listeners);
  }

  public componentDidMount(): void {
    this.loadCertificates();
  }

  private addListenerCertificate(listener: IListenerDescription): void {
    listener.certificates = listener.certificates || [];
    listener.certificates.push({
      certificateArn: undefined,
      type: 'iam',
      name: undefined,
    });
  }

  private loadCertificates(): void {
    AmazonCertificateReader.listCertificates().then((certificates) => {
      this.setState({ certificates });
    });
  }

  private listenerProtocolChanged(listener: IListenerDescription, newProtocol: NLBListenerProtocol): void {
    listener.protocol = newProtocol;
    if (listener.protocol === 'TCP') {
      listener.port = 80;
    } else if (listener.protocol === 'UDP') {
      listener.port = 53;
    } else if (listener.protocol === 'TLS') {
      listener.port = 443;
      if (!listener.certificates || listener.certificates.length === 0) {
        this.addListenerCertificate(listener);
      }
      this.reenableAuthActions(listener);
    }
    this.updateListeners();
  }

  private reenableAuthActions(listener: IListenerDescription): void {
    const removedAuthActions = this.removedAuthActions.has(listener) ? this.removedAuthActions.get(listener) : [];
    const existingDefaultAuthAction = removedAuthActions[-1];
    if (existingDefaultAuthAction) {
      removedAuthActions[-1] = undefined;
      listener.defaultActions.unshift({ ...existingDefaultAuthAction });
    }
    listener.rules.forEach((rule, ruleIndex) => {
      const existingAuthAction = removedAuthActions[ruleIndex];
      removedAuthActions[ruleIndex] = undefined;
      if (existingAuthAction) {
        rule.actions.unshift({ ...existingAuthAction });
      }
    });
  }

  private listenerPortChanged(listener: IListenerDescription, newPort: string): void {
    listener.port = Number.parseInt(newPort, 10);
    this.updateListeners();
  }

  private removeListener(index: number): void {
    this.props.formik.values.listeners.splice(index, 1);
    this.updateListeners();
  }

  private addListener = (): void => {
    this.props.formik.values.listeners.push({
      certificates: [],
      protocol: 'TCP',
      port: 80,
      defaultActions: [
        {
          type: 'forward',
          targetGroupName: '',
        },
      ],
      rules: [],
    });
    this.updateListeners();
  };

  private handleDefaultTargetChanged = (listener: IListenerDescription, newTarget: string): void => {
    listener.defaultActions[0].targetGroupName = newTarget;
    this.updateListeners();
  };

  public render() {
    const { errors, values } = this.props.formik;
    const { certificates, certificateTypes } = this.state;
    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-12">
            {values.listeners.map((listener, index) => (
              <div key={index} className="wizard-pod">
                <div>
                  <div className="wizard-pod-row header">
                    <div className="wizard-pod-row-title">Listen On</div>
                    <div className="wizard-pod-row-contents spread">
                      <div>
                        <span className="wizard-pod-content">
                          <label>Protocol</label>
                          <select
                            className="form-control input-sm inline-number"
                            style={{ width: '80px' }}
                            value={listener.protocol}
                            onChange={(event) =>
                              this.listenerProtocolChanged(listener, event.target.value as NLBListenerProtocol)
                            }
                          >
                            {this.protocols.map((p) => (
                              <option key={p}>{p}</option>
                            ))}
                          </select>
                        </span>
                        <span className="wizard-pod-content">
                          <label>Port</label>
                          <input
                            className="form-control input-sm inline-number"
                            type="text"
                            min={0}
                            value={listener.port || ''}
                            onChange={(event) => this.listenerPortChanged(listener, event.target.value)}
                            style={{ width: '80px' }}
                            required={true}
                          />
                        </span>
                      </div>
                      <div>
                        <a className="sm-label clickable" onClick={() => this.removeListener(index)}>
                          <span className="glyphicon glyphicon-trash" />
                        </a>
                      </div>
                    </div>
                  </div>
                  <div>
                    {listener.protocol === 'TLS' && (
                      <CertificateSelector
                        availableCertificates={listener.certificates}
                        formik={this.props.formik}
                        app={this.props.app}
                        certificateTypes={certificateTypes}
                        certificates={certificates}
                      ></CertificateSelector>
                    )}
                  </div>
                  <div className="wizard-pod-row">
                    <div className="wizard-pod-row-title" style={{ height: '30px' }}>
                      Rules
                    </div>
                    <div className="wizard-pod-row-contents" style={{ padding: '0' }}>
                      <table className="table table-condensed packed rules-table">
                        <thead>
                          <tr>
                            <th style={{ width: '10px', padding: '0' }} />
                            <th style={{ width: '226px' }}>If</th>
                            <th style={{ width: '75px' }}>Then</th>
                            <th>Target</th>
                            <th style={{ width: '30px' }} />
                          </tr>
                        </thead>
                        <tbody>
                          <tr className="not-sortable">
                            <td />
                            <td>Default</td>
                            <td>forward to</td>
                            <td>
                              <select
                                className="form-control input-sm"
                                value={listener.defaultActions[0].targetGroupName}
                                onChange={(event) => this.handleDefaultTargetChanged(listener, event.target.value)}
                                required={true}
                              >
                                <option value="" />
                                {uniq(values.targetGroups.map((tg) => tg.name)).map((name) => (
                                  <option key={name}>{name}</option>
                                ))}
                              </select>
                            </td>
                            <td />
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>
            ))}
            {errors.listenerPorts && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage type="error" message={errors.listenerPorts} />
              </div>
            )}
            {errors.listeners && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage type="error" message={errors.listeners} />
              </div>
            )}
            <table className="table table-condensed packed">
              <tbody>
                <tr>
                  <td>
                    <button type="button" className="add-new col-md-12" onClick={this.addListener}>
                      <span>
                        <span className="glyphicon glyphicon-plus-sign" /> Add new listener
                      </span>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  }
}
