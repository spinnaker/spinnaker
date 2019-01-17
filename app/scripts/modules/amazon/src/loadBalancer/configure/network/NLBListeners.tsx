import * as React from 'react';
import { difference, flatten, uniq } from 'lodash';

import { IWizardPageProps, ValidationMessage, wizardPage } from '@spinnaker/core';

import { NLBListenerProtocol, IListenerDescription, IAmazonNetworkLoadBalancerUpsertCommand } from 'amazon/domain';

export type INLBListenersProps = IWizardPageProps<IAmazonNetworkLoadBalancerUpsertCommand>;

class NLBListenersImpl extends React.Component<INLBListenersProps> {
  public static LABEL = 'Listeners';
  public protocols = ['TCP'];

  private getAllTargetGroupsFromListeners(listeners: IListenerDescription[]): string[] {
    const actions = flatten(listeners.map(l => l.defaultActions));
    const rules = flatten(listeners.map(l => l.rules));
    actions.push(...flatten(rules.map(r => r.actions)));
    return uniq(actions.map(a => a.targetGroupName));
  }

  public validate(values: IAmazonNetworkLoadBalancerUpsertCommand) {
    const errors = {} as any;

    // Check to make sure all target groups have an associated listener
    const targetGroupNames = values.targetGroups.map(tg => tg.name);
    const usedTargetGroupNames = this.getAllTargetGroupsFromListeners(values.listeners);
    const unusedTargetGroupNames = difference(targetGroupNames, usedTargetGroupNames);
    if (unusedTargetGroupNames.length === 1) {
      errors.listeners = `Target group ${unusedTargetGroupNames[0]} is unused.`;
    } else if (unusedTargetGroupNames.length > 1) {
      errors.listeners = `Target groups ${unusedTargetGroupNames.join(', ')} are unused.`;
    }

    return errors;
  }

  private updateListeners(): void {
    this.props.formik.setFieldValue('listeners', this.props.formik.values.listeners);
  }

  private listenerProtocolChanged(listener: IListenerDescription, newProtocol: NLBListenerProtocol): void {
    listener.protocol = newProtocol;
    if (listener.protocol === 'TCP') {
      listener.port = 80;
    }
    this.updateListeners();
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
                            onChange={event =>
                              this.listenerProtocolChanged(listener, event.target.value as NLBListenerProtocol)
                            }
                          >
                            {this.protocols.map(p => (
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
                            onChange={event => this.listenerPortChanged(listener, event.target.value)}
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
                                onChange={event => this.handleDefaultTargetChanged(listener, event.target.value)}
                                required={true}
                              >
                                <option value="" />
                                {uniq(values.targetGroups.map(tg => tg.name)).map(name => (
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

export const NLBListeners = wizardPage(NLBListenersImpl);
