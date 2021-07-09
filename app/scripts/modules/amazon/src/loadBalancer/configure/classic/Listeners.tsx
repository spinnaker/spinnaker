import { FormikProps } from 'formik';
import { get } from 'lodash';
import React from 'react';

import { Application } from '@spinnaker/core';

import { AWSProviderSettings } from '../../../aws.settings';
import { AmazonCertificateReader } from '../../../certificates/AmazonCertificateReader';
import { AmazonCertificateSelectField } from '../common/AmazonCertificateSelectField';
import {
  ClassicListenerProtocol,
  IAmazonCertificate,
  IAmazonClassicLoadBalancerUpsertCommand,
  IClassicListenerDescription,
} from '../../../domain';

import './Listeners.less';

export interface IListenersProps {
  formik: FormikProps<IAmazonClassicLoadBalancerUpsertCommand>;
  app: Application;
}

export interface IListenersState {
  certificates: { [accountId: number]: IAmazonCertificate[] };
}

export class Listeners extends React.Component<IListenersProps, IListenersState> {
  public protocols = ['HTTP', 'HTTPS', 'TCP', 'SSL'];
  public secureProtocols = ['HTTPS', 'SSL'];
  private certificateTypes = get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']);

  public state: IListenersState = {
    certificates: [],
  };

  public componentDidMount(): void {
    this.loadCertificates();
  }

  private loadCertificates(): void {
    AmazonCertificateReader.listCertificates().then((certificates) => {
      this.setState({ certificates });
    });
  }

  private updateListeners(): void {
    const { values, setFieldValue } = this.props.formik;
    setFieldValue('listeners', values.listeners);
  }

  private showCertificateSelect(listener: IClassicListenerDescription): boolean {
    return (
      listener.sslCertificateType === 'iam' &&
      this.state.certificates &&
      Object.keys(this.state.certificates).length > 0
    );
  }

  private listenerExternalProtocolChanged(
    listener: IClassicListenerDescription,
    newProtocol: ClassicListenerProtocol,
  ): void {
    listener.externalProtocol = newProtocol;
    if (this.secureProtocols.includes(newProtocol)) {
      listener.externalPort = 443;
      if (this.certificateTypes.length >= 1) {
        listener.sslCertificateType = this.certificateTypes[0];
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
    this.props.formik.values.listeners.splice(index, 1);
    this.updateListeners();
  }

  private addListener = (): void => {
    this.props.formik.values.listeners.push({
      internalProtocol: 'HTTP',
      externalProtocol: 'HTTP',
      externalPort: 80,
      internalPort: 80,
    });
    this.updateListeners();
  };

  private renderCertificateSelector(listener: IClassicListenerDescription): JSX.Element {
    if (!this.secureProtocols.includes(listener.externalProtocol)) {
      return null;
    } else if (this.showCertificateSelect(listener)) {
      const { values } = this.props.formik;
      const { certificates } = this.state;
      return (
        <AmazonCertificateSelectField
          certificates={certificates}
          accountName={values.credentials}
          currentValue={listener.sslCertificateName}
          app={this.props.app}
          onCertificateSelect={(value) => this.handleListenerCertificateChanged(listener, value)}
        />
      );
    } else {
      return (
        <input
          className="input-sm"
          style={{ width: '100%', marginLeft: '10px' }}
          required={true}
          onChange={(e) => this.handleListenerCertificateChanged(listener, e.target.value)}
          value={listener.sslCertificateName}
        />
      );
    }
  }

  public render() {
    const { values } = this.props.formik;

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
                  <th />
                </tr>
              </thead>
              <tbody>
                {values.listeners.map((listener, index) => (
                  <React.Fragment key={index}>
                    {/* Using React.Fragment to supply the key only */}
                    <tr key={index + '-main'}>
                      <td>
                        <select
                          className="form-control input-sm"
                          value={listener.externalProtocol}
                          onChange={(event) =>
                            this.listenerExternalProtocolChanged(
                              listener,
                              event.target.value as ClassicListenerProtocol,
                            )
                          }
                        >
                          {this.protocols.map((p) => (
                            <option key={p}>{p}</option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <input
                          className="form-control input-sm"
                          type="number"
                          min="0"
                          value={listener.externalPort}
                          onChange={(event) => this.listenerExternalPortChanged(listener, event.target.value)}
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
                          onChange={(event) =>
                            this.listenerInternalProtocolChanged(
                              listener,
                              event.target.value as ClassicListenerProtocol,
                            )
                          }
                        >
                          {this.protocols.map((p) => (
                            <option key={p}>{p}</option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <input
                          className="form-control input-sm"
                          type="number"
                          min="0"
                          value={listener.internalPort}
                          onChange={(event) => this.listenerInternalPortChanged(listener, event.target.value)}
                          required={true}
                        />
                      </td>
                      <td>
                        <a className="sm-label clickable" onClick={() => this.removeListener(index)}>
                          <span className="glyphicon glyphicon-trash" />
                        </a>
                      </td>
                    </tr>
                    {this.secureProtocols.includes(listener.externalProtocol) && (
                      <tr key={index + '-ssl'}>
                        <td colSpan={5} style={{ borderTopWidth: 0 }}>
                          <div className="horizontal space-between">
                            <div className="sm-label-right">Certificate</div>
                            {this.certificateTypes.length > 1 && (
                              <select
                                style={{ width: '45px', marginLeft: '10px' }}
                                className="form-control input-sm"
                                value={listener.sslCertificateType}
                                onChange={(event) => this.listenerCertificateTypeChanged(listener, event.target.value)}
                              >
                                {this.certificateTypes.map((t) => (
                                  <option key={t}>{t}</option>
                                ))}
                              </select>
                            )}
                            {this.renderCertificateSelector(listener)}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={5}>
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
