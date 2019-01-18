import * as React from 'react';
import { get } from 'lodash';
import { FormikErrors } from 'formik';

import { IWizardPageProps, wizardPage } from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import {
  ClassicListenerProtocol,
  IAmazonCertificate,
  IAmazonClassicLoadBalancerUpsertCommand,
  IClassicListenerDescription,
} from 'amazon/domain';
import { AmazonCertificateReader } from 'amazon/certificates/AmazonCertificateReader';
import { AmazonCertificateSelectField } from '../common/AmazonCertificateSelectField';

import './Listeners.less';

export type IListenersProps = IWizardPageProps<IAmazonClassicLoadBalancerUpsertCommand>;

export interface IListenersState {
  certificates: { [accountId: number]: IAmazonCertificate[] };
  certificateTypes: string[];
}

class ListenersImpl extends React.Component<IListenersProps, IListenersState> {
  public static LABEL = 'Listeners';
  public protocols = ['HTTP', 'HTTPS', 'TCP', 'SSL'];
  public secureProtocols = ['HTTPS', 'SSL'];

  public state: IListenersState = {
    certificates: [],
    certificateTypes: get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']),
  };

  public validate() {
    return {} as FormikErrors<IAmazonClassicLoadBalancerUpsertCommand>;
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
    const { values, setFieldValue } = this.props.formik;
    setFieldValue('listeners', values.listeners);
  }

  private someListenersUseSSL(): boolean {
    const { listeners } = this.props.formik.values;
    return listeners.some(listener => this.secureProtocols.includes(listener.externalProtocol));
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
    this.props.formik.values.listeners.splice(index, 1);
    this.updateListeners();
  }

  private addListener = (): void => {
    this.props.formik.values.listeners.push({
      internalProtocol: 'HTTP',
      externalProtocol: 'HTTP',
      externalPort: 80,
      internalPort: 0,
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
          accountId={values.credentials as any}
          currentValue={listener.sslCertificateName}
          onCertificateSelect={value => this.handleListenerCertificateChanged(listener, value)}
        />
      );
    } else {
      return (
        <input
          className="input-sm"
          required={true}
          onChange={e => this.handleListenerCertificateChanged(listener, e.target.value)}
          value={listener.sslCertificateName}
        />
      );
    }
  }

  public render() {
    const { values } = this.props.formik;
    const { certificateTypes } = this.state;

    const showCertNameColumn = this.someListenersUseSSL();
    const showCertTypeColumn = showCertNameColumn && certificateTypes.length > 1;
    const colSpan = showCertTypeColumn ? 7 : showCertNameColumn ? 6 : 5;

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
                  {showCertTypeColumn && <th style={{ width: '10%' }}>SSL Certificate Type</th>}
                  {showCertNameColumn && <th style={{ width: '30%' }}>SSL Certificate Name</th>}
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
                        {this.protocols.map(p => (
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
                        {this.protocols.map(p => (
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
                        onChange={event => this.listenerInternalPortChanged(listener, event.target.value)}
                        required={true}
                      />
                    </td>

                    {showCertTypeColumn && (
                      <td>
                        {this.secureProtocols.includes(listener.externalProtocol) && (
                          <select
                            className="form-control input-sm"
                            value={listener.sslCertificateType}
                            onChange={event => this.listenerCertificateTypeChanged(listener, event.target.value)}
                          >
                            {this.state.certificateTypes.map(t => (
                              <option key={t}>{t}</option>
                            ))}
                          </select>
                        )}
                      </td>
                    )}

                    {showCertNameColumn && <td>{this.renderCertificateSelector(listener)}</td>}

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
                  <td colSpan={colSpan}>
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
