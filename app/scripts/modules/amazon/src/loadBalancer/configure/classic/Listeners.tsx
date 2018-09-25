import * as React from 'react';
import Select, { Option } from 'react-select';
import { get } from 'lodash';
import { FormikProps } from 'formik';

import { IWizardPageProps, wizardPage } from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import {
  ClassicListenerProtocol,
  IAmazonClassicLoadBalancerUpsertCommand,
  IClassicListenerDescription,
} from 'amazon/domain';
import { AmazonCertificateReader, IAmazonCertificate } from 'amazon/certificates/AmazonCertificateReader';

import './Listeners.less';

export interface IListenersState {
  certificates: { [accountId: number]: IAmazonCertificate[] };
  certificateTypes: string[];
}

class ListenersImpl extends React.Component<
  IWizardPageProps & FormikProps<IAmazonClassicLoadBalancerUpsertCommand>,
  IListenersState
> {
  public static LABEL = 'Listeners';
  public protocols = ['HTTP', 'HTTPS', 'TCP', 'SSL'];
  public secureProtocols = ['HTTPS', 'SSL'];

  constructor(props: IWizardPageProps & FormikProps<IAmazonClassicLoadBalancerUpsertCommand>) {
    super(props);
    this.state = {
      certificates: [],
      certificateTypes: get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']),
    };
  }

  public validate(): { [key: string]: string } {
    return {};
  }

  public componentDidMount(): void {
    this.loadCertificates();
  }

  private loadCertificates(): void {
    AmazonCertificateReader.listCertificates().then(certificates => {
      this.setState({ certificates });
    });
  }

  private updateListeners(): void {
    this.props.setFieldValue('listeners', this.props.values.listeners);
  }

  private needsCert(): boolean {
    return this.props.values.listeners.some(listener => this.secureProtocols.includes(listener.externalProtocol));
  }

  private showCertificateSelect(listener: IClassicListenerDescription): boolean {
    return (
      listener.sslCertificateType === 'iam' &&
      this.secureProtocols.includes(listener.externalProtocol) &&
      this.state.certificates &&
      Object.keys(this.state.certificates).length > 0
    );
  }

  private listenerExternalProtocolChanged(
    listener: IClassicListenerDescription,
    newProtocol: ClassicListenerProtocol,
  ): void {
    listener.externalProtocol = newProtocol;
    if (newProtocol === 'HTTPS' || newProtocol === 'SSL') {
      listener.externalPort = 443;
      if (this.state.certificateTypes.length >= 1) {
        listener.sslCertificateType = this.state.certificateTypes[0];
      }
    }
    if (newProtocol === 'HTTP') {
      listener.externalPort = 80;
    }
    this.updateListeners();
  }

  private listenerInternalProtocolChanged(
    listener: IClassicListenerDescription,
    newProtocol: ClassicListenerProtocol,
  ): void {
    listener.internalProtocol = newProtocol;
    this.updateListeners();
  }

  private listenerExternalPortChanged(listener: IClassicListenerDescription, newPort: string): void {
    listener.externalPort = Number.parseInt(newPort, 10);
    this.updateListeners();
  }

  private listenerInternalPortChanged(listener: IClassicListenerDescription, newPort: string): void {
    listener.internalPort = Number.parseInt(newPort, 10);
    this.updateListeners();
  }

  private listenerCertificateTypeChanged(listener: IClassicListenerDescription, newProtocol: string): void {
    listener.sslCertificateType = newProtocol;
    this.updateListeners();
  }

  private handleListenerCertificateChanged(listener: IClassicListenerDescription, newCertificate: string): void {
    listener.sslCertificateName = newCertificate;
    this.updateListeners();
  }

  private removeListener(index: number): void {
    this.props.values.listeners.splice(index, 1);
    this.updateListeners();
  }

  private addListener = (): void => {
    this.props.values.listeners.push({
      internalProtocol: 'HTTP',
      externalProtocol: 'HTTP',
      externalPort: 80,
      internalPort: 0,
    });
    this.updateListeners();
  };

  public render() {
    const { values } = this.props;
    const { certificates, certificateTypes } = this.state;

    const certificatesForAccount = certificates[values.credentials as any] || [];
    const certificateOptions = certificatesForAccount.map(cert => {
      return { label: cert.serverCertificateName, value: cert.serverCertificateName };
    });

    return (
      <div className="container-fluid form-horizontal create-classic-load-balancer-wizard-listeners">
        <div className="form-group">
          <div className="col-md-12">
            <table className="table table-condensed packed">
              <thead>
                <tr>
                  <th>External Protocol</th>
                  <th>External Port</th>
                  <th />
                  <th>Internal Protocol</th>
                  <th>Internal Port</th>
                  {this.needsCert() &&
                    certificateTypes.length > 1 && <th style={{ width: '10%' }}>SSL Certificate Type</th>}
                  {this.needsCert() && <th style={{ width: '30%' }}>SSL Certificate Name</th>}
                  <th />
                </tr>
              </thead>
              <tbody>
                {values.listeners.map((listener, index) => (
                  <tr key={index}>
                    <td>
                      <select
                        className="form-control input-sm"
                        value={listener.externalProtocol}
                        onChange={event =>
                          this.listenerExternalProtocolChanged(listener, event.target.value as ClassicListenerProtocol)
                        }
                      >
                        {this.protocols.map(p => <option key={p}>{p}</option>)}
                      </select>
                    </td>
                    <td>
                      <input
                        className="form-control input-sm"
                        type="number"
                        min="0"
                        value={listener.externalPort}
                        onChange={event => this.listenerExternalPortChanged(listener, event.target.value)}
                        required={true}
                      />
                    </td>
                    <td className="small" style={{ paddingTop: '10px' }}>
                      &rarr;
                    </td>
                    <td>
                      <select
                        className="form-control input-sm"
                        value={listener.internalProtocol}
                        onChange={event =>
                          this.listenerInternalProtocolChanged(listener, event.target.value as ClassicListenerProtocol)
                        }
                      >
                        {this.protocols.map(p => <option key={p}>{p}</option>)}
                      </select>
                    </td>
                    <td>
                      <input
                        className="form-control input-sm"
                        type="number"
                        min="0"
                        value={listener.internalPort}
                        onChange={event => this.listenerInternalPortChanged(listener, event.target.value)}
                        required={true}
                      />
                    </td>
                    {this.needsCert() &&
                      certificateTypes.length > 1 && (
                        <td>
                          {this.secureProtocols.includes(listener.externalProtocol) && (
                            <select
                              className="form-control input-sm"
                              value={listener.sslCertificateType}
                              onChange={event => this.listenerCertificateTypeChanged(listener, event.target.value)}
                            >
                              {this.state.certificateTypes.map(t => <option key={t}>{t}</option>)}
                            </select>
                          )}
                        </td>
                      )}
                    {this.needsCert() && (
                      <td>
                        {this.showCertificateSelect(listener) ? (
                          <Select
                            className="input-sm"
                            clearable={false}
                            required={true}
                            options={certificateOptions}
                            onChange={(value: Option<string>) =>
                              this.handleListenerCertificateChanged(listener, value.value)
                            }
                            value={listener.sslCertificateName}
                          />
                        ) : (
                          <input
                            className="input-sm"
                            required={true}
                            onChange={e => this.handleListenerCertificateChanged(listener, e.target.value)}
                            value={listener.sslCertificateName}
                          />
                        )}
                      </td>
                    )}
                    <td>
                      <a className="sm-label clickable" onClick={() => this.removeListener(index)}>
                        <span className="glyphicon glyphicon-trash" />
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={this.needsCert() ? 7 : 5}>
                    <button className="add-new col-md-12" onClick={this.addListener} type="button">
                      <span className="glyphicon glyphicon-plus-sign" />
                      <span> Add new port mapping</span>
                    </button>
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      </div>
    );
  }
}

export const Listeners = wizardPage(ListenersImpl);
