import React from 'react';
import { $q } from 'ngimport';
import { SortableContainer, SortableElement, SortableHandle, arrayMove, SortEnd } from 'react-sortable-hoc';
import { difference, flatten, get, some, uniq, uniqBy } from 'lodash';
import { FormikErrors, FormikProps } from 'formik';

import {
  Application,
  ConfirmationModalService,
  CustomLabels,
  HelpField,
  IWizardPageComponent,
  Tooltip,
  ValidationMessage,
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import {
  ALBListenerProtocol,
  IALBListenerCertificate,
  IAmazonCertificate,
  IListenerDescription,
  IALBTargetGroupDescription,
  IAmazonApplicationLoadBalancerUpsertCommand,
  IListenerAction,
  IListenerRule,
  IListenerRuleCondition,
  ListenerRuleConditionField,
  IRedirectActionConfig,
  IListenerActionType,
} from 'amazon/domain';
import { AmazonCertificateReader } from 'amazon/certificates/AmazonCertificateReader';
import { IAuthenticateOidcActionConfig, OidcConfigReader } from '../../OidcConfigReader';

import { ConfigureOidcConfigModal } from './ConfigureOidcConfigModal';
import { AmazonCertificateSelectField } from '../common/AmazonCertificateSelectField';
import { ConfigureRedirectConfigModal } from './ConfigureRedirectConfigModal';

export interface IALBListenersState {
  certificates: { [accountId: number]: IAmazonCertificate[] };
  certificateTypes: string[];
  oidcConfigs: IAuthenticateOidcActionConfig[];
}

const DragHandle = SortableHandle(() => (
  <span className="pipeline-drag-handle clickable glyphicon glyphicon-resize-vertical" />
));

const defaultAuthAction = {
  authenticateOidcConfig: {
    authorizationEndpoint: '',
    clientId: '',
    issuer: '',
    scope: 'openid',
    sessionCookieName: 'AWSELBAuthSessionCookie',
    tokenEndpoint: '',
    userInfoEndpoint: '',
  },
  type: 'authenticate-oidc',
} as IListenerAction;

export interface IALBListenersProps {
  app: Application;
  formik: FormikProps<IAmazonApplicationLoadBalancerUpsertCommand>;
}

export class ALBListeners
  extends React.Component<IALBListenersProps, IALBListenersState>
  implements IWizardPageComponent<IAmazonApplicationLoadBalancerUpsertCommand> {
  public protocols = ['HTTP', 'HTTPS'];

  private initialActionsWithAuth: Set<IListenerAction[]> = new Set();
  private initialListenersWithDefaultAuth: Set<IListenerDescription> = new Set();
  private removedAuthActions: Map<IListenerDescription, { [key: number]: IListenerAction }> = new Map();

  constructor(props: IALBListenersProps) {
    super(props);
    this.state = {
      certificates: [],
      certificateTypes: get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']),
      oidcConfigs: undefined,
    };

    this.props.formik.initialValues.listeners.forEach((l) => {
      const hasDefaultAuth = l.defaultActions[0].type === 'authenticate-oidc';
      if (hasDefaultAuth) {
        this.initialListenersWithDefaultAuth.add(l);
      }
      l.rules.forEach((r) => {
        if (r.actions[0].type === 'authenticate-oidc') {
          this.initialActionsWithAuth.add(r.actions);
        }
      });
    });
  }

  private getAllTargetGroupsFromListeners(listeners: IListenerDescription[]): string[] {
    const actions = flatten(listeners.map((l) => l.defaultActions));
    const rules = flatten(listeners.map((l) => l.rules));
    actions.push(...flatten(rules.map((r) => r.actions)));
    return uniq(actions.map((a) => a.targetGroupName));
  }

  public validate(
    values: IAmazonApplicationLoadBalancerUpsertCommand,
  ): FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand> {
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

    const missingRuleFields = values.listeners.find((l) => {
      const defaultActionsHaveMissingTarget = !!l.defaultActions.find(
        (da) =>
          (da.type === 'forward' && !da.targetGroupName) ||
          (da.type === 'authenticate-oidc' && !da.authenticateOidcConfig.clientId) ||
          (da.type === 'redirect' &&
            (!da.redirectActionConfig || !some(da.redirectActionConfig, (field) => field && field !== ''))),
      );
      const rulesHaveMissingFields = !!l.rules.find((rule) => {
        const missingTargets = !!rule.actions.find((a) => a.type === 'forward' && !a.targetGroupName);
        const missingAuth = !!rule.actions.find(
          (a) => a.type === 'authenticate-oidc' && !a.authenticateOidcConfig.clientId,
        );
        const missingValue = !!rule.conditions.find((c) => {
          if (c.field === 'http-request-method') {
            return !c.values.length;
          }
          return c.values.includes('');
        });

        return missingTargets || missingAuth || missingValue;
      });
      return defaultActionsHaveMissingTarget || rulesHaveMissingFields;
    });

    if (missingRuleFields) {
      errors.listeners = `Missing fields in rule configuration.`;
    }

    return errors;
  }

  public componentDidMount(): void {
    this.loadCertificates();
    this.loadOidcClients();
  }

  private loadCertificates(): void {
    AmazonCertificateReader.listCertificates().then((certificates) => {
      this.setState({ certificates });
    });
  }

  private attachClientSecret = (action: IListenerAction, oidcConfigs: IAuthenticateOidcActionConfig[]) => {
    if (action.type === 'authenticate-oidc') {
      const config = oidcConfigs.find((c) => c.clientId === action.authenticateOidcConfig.clientId);
      if (config) {
        action.authenticateOidcConfig.clientSecret = config.clientSecret;
      }
    }
  };

  private loadOidcClients(): void {
    OidcConfigReader.getOidcConfigsByApp(this.props.app.name)
      .then((oidcConfigs) => {
        // make sure we have all the secrets for listener actions that need them
        if (oidcConfigs && oidcConfigs.length) {
          this.props.formik.values.listeners.forEach((listener) => {
            listener.defaultActions.forEach((action) => this.attachClientSecret(action, oidcConfigs));
            listener.rules.forEach((rule) =>
              rule.actions.forEach((action) => this.attachClientSecret(action, oidcConfigs)),
            );
          });
        }

        this.setState({ oidcConfigs });
        this.updateListeners();
      })
      .catch(() => {});
  }

  private updateListeners(): void {
    this.props.formik.setFieldValue('listeners', this.props.formik.values.listeners);
  }

  private needsCert(listener: IListenerDescription): boolean {
    return listener.protocol === 'HTTPS';
  }

  private showCertificateSelect(certificate: IALBListenerCertificate): boolean {
    return certificate.type === 'iam' && this.state.certificates && Object.keys(this.state.certificates).length > 0;
  }

  private addListenerCertificate(listener: IListenerDescription): void {
    listener.certificates = listener.certificates || [];
    listener.certificates.push({
      certificateArn: undefined,
      type: 'iam',
      name: undefined,
    });
  }

  private removeAuthActions(listener: IListenerDescription): void {
    const authIndex = listener.defaultActions.findIndex((a) => a.type === 'authenticate-oidc');
    if (authIndex !== -1) {
      this.removeAuthAction(listener, listener.defaultActions, authIndex, -1);
    }
    listener.rules.forEach((rule, ruleIndex) => {
      const index = rule.actions.findIndex((a) => a.type === 'authenticate-oidc');
      if (index !== -1) {
        this.removeAuthAction(listener, rule.actions, index, ruleIndex);
      }
    });
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

  private listenerProtocolChanged(listener: IListenerDescription, newProtocol: ALBListenerProtocol): void {
    listener.protocol = newProtocol;
    if (listener.protocol === 'HTTPS') {
      listener.port = 443;
      if (!listener.certificates || listener.certificates.length === 0) {
        this.addListenerCertificate(listener);
      }
      this.reenableAuthActions(listener);
    }
    if (listener.protocol === 'HTTP') {
      listener.port = 80;
      listener.certificates.length = 0;
      this.removeAuthActions(listener);
    }
    this.updateListeners();
  }

  private listenerPortChanged(listener: IListenerDescription, newPort: string): void {
    listener.port = Number.parseInt(newPort, 10);
    this.updateListeners();
  }

  private certificateTypeChanged(certificate: IALBListenerCertificate, newType: string): void {
    certificate.type = newType;
    this.updateListeners();
  }

  private handleCertificateChanged(certificate: IALBListenerCertificate, newCertificateName: string): void {
    certificate.name = newCertificateName;
    this.updateListeners();
  }

  private removeListener(index: number): void {
    this.props.formik.values.listeners.splice(index, 1);
    this.updateListeners();
  }

  private addListener = (): void => {
    this.props.formik.values.listeners.push({
      certificates: [],
      protocol: 'HTTP',
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

  private addRule = (listener: IListenerDescription): void => {
    const newRule: IListenerRule = {
      priority: null,
      actions: [
        {
          type: 'forward',
          targetGroupName: '',
        },
      ],
      conditions: [
        {
          field: 'path-pattern',
          values: [''],
        },
      ],
    };

    listener.rules.push(newRule);
    this.updateListeners();
  };

  public removeRule = (listener: IListenerDescription, index: number): void => {
    listener.rules.splice(index, 1);
    this.updateListeners();
  };

  private handleConditionFieldChanged = (
    condition: IListenerRuleCondition,
    newField: ListenerRuleConditionField,
  ): void => {
    condition.field = newField;

    if (newField === 'http-request-method') {
      condition.values = [];
    }

    this.updateListeners();
  };

  private handleConditionValueChanged = (condition: IListenerRuleCondition, newValue: string): void => {
    condition.values[0] = newValue;
    this.updateListeners();
  };

  private handleHttpRequestMethodChanged = (
    condition: IListenerRuleCondition,
    newValue: string,
    selected: boolean,
  ): void => {
    let newValues = condition.values || [];

    if (selected) {
      newValues.push(newValue);
    } else {
      newValues = newValues.filter((v) => v !== newValue);
    }

    /**
     * The `http-request-method` conditions have a differnt model.
     * AWS uses `httpRequestMethodConfig` as the source of truth, while deck uses `values`.
     * Both are updated for consistency.
     */
    condition.values = newValues;
    condition.httpRequestMethodConfig = {
      values: newValues,
    };
    this.updateListeners();
  };

  private addCondition = (rule: IListenerRule): void => {
    if (rule.conditions.length === 1) {
      const field = rule.conditions[0].field === 'path-pattern' ? 'host-header' : 'path-pattern';
      rule.conditions.push({ field, values: [''] });
    }
    this.updateListeners();
  };

  private removeCondition = (rule: IListenerRule, index: number): void => {
    rule.conditions.splice(index, 1);
    this.updateListeners();
  };

  private handleRuleActionTargetChanged = (action: IListenerAction, newTarget: string): void => {
    action.targetGroupName = newTarget;
    this.updateListeners();
  };

  private handleRuleActionTypeChanged = (action: IListenerAction, newType: IListenerActionType): void => {
    action.type = newType;

    if (action.type === 'forward') {
      delete action.redirectActionConfig;
    } else if (action.type === 'redirect') {
      action.redirectActionConfig = {
        statusCode: 'HTTP_301',
      };
      delete action.targetGroupName;
    }

    this.updateListeners();
  };

  private handleSortEnd = (sortEnd: SortEnd, listener: IListenerDescription): void => {
    listener.rules = arrayMove(listener.rules, sortEnd.oldIndex, sortEnd.newIndex);
    this.updateListeners();
  };

  private configureOidcClient = (action: IListenerAction): void => {
    ConfigureOidcConfigModal.show({ config: action.authenticateOidcConfig })
      .then((config: any) => {
        action.authenticateOidcConfig = config;
        this.updateListeners(); // pushes change to formik, needed due to prop mutation
      })
      .catch(() => {});
  };

  private configureRedirect = (action: IListenerAction): void => {
    ConfigureRedirectConfigModal.show({ config: action.redirectActionConfig })
      .then((config: any) => {
        action.redirectActionConfig = config;
        this.updateListeners(); // pushes change to formik, needed due to prop mutation
      })
      .catch(() => {});
  };

  private removeAuthActionInternal(
    listener: IListenerDescription,
    actions: IListenerAction[],
    authIndex: number,
    ruleIndex = -1,
  ): void {
    const removedAuthAction = actions.splice(authIndex, 1)[0];
    if (!this.removedAuthActions.has(listener)) {
      this.removedAuthActions.set(listener, []);
    }
    this.removedAuthActions.get(listener)[ruleIndex || -1] = removedAuthAction;
    this.updateListeners();
  }

  private removeAuthAction(
    listener: IListenerDescription,
    actions: IListenerAction[],
    authIndex: number,
    ruleIndex = -1,
  ): void {
    // TODO: Check if initial is true.
    const confirmDefaultRemove = ruleIndex === -1 && this.initialListenersWithDefaultAuth.has(listener);
    const confirmRemove = ruleIndex > -1 && this.initialActionsWithAuth.has(actions);

    if (confirmDefaultRemove || confirmRemove) {
      // TODO: Confirmation Dialog first.
      ConfirmationModalService.confirm({
        header: 'Really remove authentication?',
        buttonText: `Remove Auth`,
        submitMethod: () => {
          this.removeAuthActionInternal(listener, actions, authIndex, ruleIndex);
          if (confirmDefaultRemove) {
            this.initialListenersWithDefaultAuth.delete(listener);
          }
          if (confirmRemove) {
            this.initialActionsWithAuth.delete(actions);
          }
          return $q.resolve();
        },
      });
    } else {
      this.removeAuthActionInternal(listener, actions, authIndex, ruleIndex);
    }
  }

  private authenticateRuleToggle = (listener: IListenerDescription, ruleIndex: number) => {
    const rules = listener.rules[ruleIndex];
    const actions = (rules && rules.actions) || listener.defaultActions;
    if (actions) {
      const authIndex = actions.findIndex((a) => a.type === 'authenticate-oidc');
      if (authIndex !== -1) {
        this.removeAuthAction(listener, actions, authIndex, ruleIndex);
      } else {
        const removedAction = this.removedAuthActions.has(listener)
          ? this.removedAuthActions.get(listener)[ruleIndex || -1]
          : undefined;
        const newAuthAction = removedAction || { ...defaultAuthAction };
        actions.unshift({ ...newAuthAction });
      }
      this.updateListeners();
    }
  };

  private oidcConfigChanged = (action: IListenerAction, config: IAuthenticateOidcActionConfig) => {
    action.authenticateOidcConfig = { ...config };
    this.updateListeners();
  };

  private redirectConfigChanged = (action: IListenerAction, config: IRedirectActionConfig) => {
    action.redirectActionConfig = { ...config };
    this.updateListeners();
  };

  public render() {
    const { errors, values } = this.props.formik;
    const { certificates, certificateTypes, oidcConfigs } = this.state;
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
                              this.listenerProtocolChanged(listener, event.target.value as ALBListenerProtocol)
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
                  {this.needsCert(listener) && (
                    <div className="wizard-pod-row">
                      <div className="wizard-pod-row-title">Certificate</div>
                      <div className="wizard-pod-row-contents">
                        {listener.certificates.map((certificate, cIndex) => (
                          <div key={cIndex} style={{ width: '100%', display: 'flex', flexDirection: 'row' }}>
                            <select
                              className="form-control input-sm inline-number"
                              style={{ width: '45px' }}
                              value={certificate.type}
                              onChange={(event) => this.certificateTypeChanged(certificate, event.target.value)}
                            >
                              {certificateTypes.map((t) => (
                                <option key={t}>{t}</option>
                              ))}
                            </select>
                            {this.showCertificateSelect(certificate) && (
                              <AmazonCertificateSelectField
                                certificates={certificates}
                                accountName={values.credentials}
                                currentValue={certificate.name}
                                app={this.props.app}
                                onCertificateSelect={(value) => this.handleCertificateChanged(certificate, value)}
                              />
                            )}
                            {!this.showCertificateSelect(certificate) && (
                              <input
                                className="form-control input-sm no-spel"
                                style={{ display: 'inline-block' }}
                                type="text"
                                value={certificate.name}
                                onChange={(event) => this.handleCertificateChanged(certificate, event.target.value)}
                                required={true}
                              />
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                  <div className="wizard-pod-row">
                    <div className="wizard-pod-row-contents" style={{ padding: '0' }}>
                      <table className="table table-condensed packed rules-table">
                        <thead>
                          <tr>
                            <th style={{ width: '15px', padding: '0' }} />
                            <th>If</th>
                            <th style={{ width: '315px' }}>Then</th>
                            <th style={{ width: '45px' }} />
                          </tr>
                        </thead>
                        <Rules
                          addCondition={this.addCondition}
                          addRule={this.addRule}
                          authenticateRuleToggle={this.authenticateRuleToggle}
                          distance={10}
                          handleConditionFieldChanged={this.handleConditionFieldChanged}
                          handleConditionValueChanged={this.handleConditionValueChanged}
                          handleHttpRequestMethodChanged={this.handleHttpRequestMethodChanged}
                          handleRuleActionTargetChanged={this.handleRuleActionTargetChanged}
                          handleRuleActionTypeChanged={this.handleRuleActionTypeChanged}
                          listener={listener}
                          helperClass="rule-sortable-helper"
                          removeRule={this.removeRule}
                          removeCondition={this.removeCondition}
                          targetGroups={values.targetGroups}
                          oidcConfigs={oidcConfigs}
                          oidcConfigChanged={this.oidcConfigChanged}
                          redirectConfigChanged={this.redirectConfigChanged}
                          onSortEnd={(sortEnd) => this.handleSortEnd(sortEnd, listener)}
                          configureOidcClient={this.configureOidcClient}
                          configureRedirect={this.configureRedirect}
                        />
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

interface IRuleProps {
  rule: IListenerRule;
  listener: IListenerDescription;
  index: number;
  targetGroups: IALBTargetGroupDescription[];
  oidcConfigChanged: (action: IListenerAction, config: IAuthenticateOidcActionConfig) => void;
  redirectConfigChanged: (action: IListenerAction, config: IRedirectActionConfig) => void;
  oidcConfigs: IAuthenticateOidcActionConfig[];
  ruleIndex: number;
  authenticateRuleToggle: (listener: IListenerDescription, index: number) => void;
  removeRule: (listener: IListenerDescription, index: number) => void;
  handleRuleActionTargetChanged: (action: IListenerAction, newTarget: string) => void;
  handleRuleActionTypeChanged: (action: IListenerAction, newType: string) => void;
  addCondition: (rule: IListenerRule) => void;
  removeCondition: (rule: IListenerRule, index: number) => void;
  handleConditionFieldChanged: (condition: IListenerRuleCondition, newField: ListenerRuleConditionField) => void;
  handleConditionValueChanged: (condition: IListenerRuleCondition, newValue: string) => void;
  handleHttpRequestMethodChanged: (condition: IListenerRuleCondition, newValue: string, selected: boolean) => void;
  configureOidcClient: (action: IListenerAction) => void;
  configureRedirect: (action: IListenerAction) => void;
}

const Rule = SortableElement((props: IRuleProps) => (
  <tr className="listener-rule">
    <td className="handle">
      <DragHandle />
    </td>
    <td>
      {props.rule.conditions.map((condition, cIndex) => (
        <div key={cIndex} className="listener-rule-condition">
          <select
            className="form-control input-sm inline-number"
            value={condition.field}
            onChange={(event) =>
              props.handleConditionFieldChanged(condition, event.target.value as ListenerRuleConditionField)
            }
            style={{ width: '40%' }}
            required={true}
          >
            {(props.rule.conditions.length === 1 || condition.field === 'host-header') && (
              <option value="host-header">Host</option>
            )}
            {(props.rule.conditions.length === 1 || condition.field === 'path-pattern') && (
              <option value="path-pattern">Path</option>
            )}
            {(props.rule.conditions.length === 1 || condition.field === 'http-request-method') && (
              <option value="http-request-method">Method(s)</option>
            )}
          </select>
          {condition.field === 'path-pattern' && <HelpField id="aws.loadBalancer.ruleCondition.path" />}
          {condition.field === 'host-header' && <HelpField id="aws.loadBalancer.ruleCondition.host" />}
          {condition.field !== 'http-request-method' && (
            <input
              className="form-control input-sm"
              type="text"
              value={condition.values[0]}
              onChange={(event) => props.handleConditionValueChanged(condition, event.target.value)}
              maxLength={128}
              required={true}
              style={{ width: '63%' }}
            />
          )}
          {condition.field === 'http-request-method' && (
            <div className="col-md-6 checkbox">
              {['DELETE', 'GET', 'PATCH', 'POST', 'PUT'].map((httpMethod) => (
                <label key={`${httpMethod}-checkbox`}>
                  <input
                    type="checkbox"
                    checked={condition.values.includes(httpMethod)}
                    onChange={(event) =>
                      props.handleHttpRequestMethodChanged(condition, httpMethod, event.target.checked)
                    }
                  />
                  {httpMethod}
                </label>
              ))}
            </div>
          )}
          <span className="remove-condition">
            {cIndex === 1 && (
              <a
                className="btn btn-sm btn-link clickable"
                onClick={() => props.removeCondition(props.rule, cIndex)}
                style={{ padding: '0' }}
              >
                <Tooltip value="Remove Condition">
                  <span className="glyphicon glyphicon-trash" />
                </Tooltip>
              </a>
            )}
          </span>
        </div>
      ))}
      {props.rule.conditions.length === 1 && (
        <div className="add-new-container">
          <button type="button" className="add-new col-md-12" onClick={() => props.addCondition(props.rule)}>
            <span>
              <span className="glyphicon glyphicon-plus-sign" /> Add new condition
            </span>
          </button>
          <span style={{ minWidth: '15px' }} />
        </div>
      )}
    </td>
    <td>
      {props.rule.actions.map((action, index) => (
        <Action
          key={index}
          action={action}
          actionTypeChanged={(type) => props.handleRuleActionTypeChanged(action, type)}
          oidcConfigChanged={(config) => props.oidcConfigChanged(action, config)}
          redirectConfigChanged={(config) => props.redirectConfigChanged(action, config)}
          targetChanged={(target) => props.handleRuleActionTargetChanged(action, target)}
          targetGroups={props.targetGroups}
          oidcConfigs={props.oidcConfigs}
          configureOidcClient={props.configureOidcClient}
          configureRedirect={props.configureRedirect}
        />
      ))}
    </td>
    <td>
      <RuleActions
        ruleIndex={props.ruleIndex}
        listener={props.listener}
        authenticateRuleToggle={props.authenticateRuleToggle}
        removeRule={props.removeRule}
        actions={props.rule.actions}
      />
    </td>
  </tr>
));

const Action = (props: {
  action: IListenerAction;
  oidcConfigChanged: (config: IAuthenticateOidcActionConfig) => void;
  redirectConfigChanged: (config: IRedirectActionConfig) => void;
  actionTypeChanged: (actionType: string) => void;
  targetChanged: (newTarget: string) => void;
  targetGroups: IALBTargetGroupDescription[];
  oidcConfigs: IAuthenticateOidcActionConfig[];
  configureOidcClient: (action: IListenerAction) => void;
  configureRedirect: (action: IListenerAction) => void;
}) => {
  if (props.action.type !== 'authenticate-oidc') {
    const redirectConfig = props.action.redirectActionConfig || props.action.redirectConfig;
    // TODO: Support redirect
    return (
      <div className="horizontal top">
        <select
          className="form-control input-sm"
          style={{ width: '80px' }}
          value={props.action.type}
          onChange={(event) => props.actionTypeChanged(event.target.value)}
        >
          <option value="forward">forward to</option>
          <option value="redirect">redirect to</option>
        </select>
        {props.action.type === 'forward' && (
          <select
            className="form-control input-sm"
            value={props.action.targetGroupName}
            onChange={(event) => props.targetChanged(event.target.value)}
            required={true}
          >
            <option value="" />
            {uniq(props.targetGroups.map((tg) => tg.name)).map((name) => (
              <option key={name}>{name}</option>
            ))}
          </select>
        )}
        {props.action.type === 'redirect' && (
          <dl className="dl-horizontal dl-narrow">
            <dt>Host</dt>
            <dd>{redirectConfig.host}</dd>
            <dt>Path</dt>
            <dd>{redirectConfig.path}</dd>
            <dt>Port</dt>
            <dd>{redirectConfig.port}</dd>
            <dt>Protocol</dt>
            <dd>{redirectConfig.protocol}</dd>
            <dt>Status Code</dt>
            <dd>{redirectConfig.statusCode}</dd>
            <dt>
              <button
                className="btn btn-link no-padding"
                type="button"
                onClick={() => props.configureRedirect(props.action)}
              >
                Configure...
              </button>
            </dt>
          </dl>
        )}
      </div>
    );
  }
  if (props.action.type === 'authenticate-oidc') {
    const clientId = props.action.authenticateOidcConfig.clientId;

    const disableManualOidcDialog = get(AWSProviderSettings, 'loadBalancers.disableManualOidcDialog', false);
    const showOidcConfigs =
      disableManualOidcDialog ||
      (props.oidcConfigs &&
        props.oidcConfigs.length > 0 &&
        (!clientId || props.oidcConfigs.find((c) => c.clientId === clientId)));

    const oidcOptions = props.oidcConfigs?.length ? (
      props.oidcConfigs.map((config) => <option key={config.clientId}>{config.clientId}</option>)
    ) : (
      <option disabled>No {CustomLabels.get('OIDC client')} config found</option>
    );

    return (
      <div className="horizontal middle" style={{ height: '30px' }}>
        <span style={{ whiteSpace: 'pre' }}>auth with {CustomLabels.get('OIDC client')} </span>

        {showOidcConfigs && (
          <select
            className="form-control input-sm"
            value={clientId}
            onChange={(event) =>
              props.oidcConfigChanged(props.oidcConfigs.find((c) => c.clientId === event.target.value))
            }
            required={true}
          >
            <option value="" />
            {oidcOptions}
          </select>
        )}
        {!showOidcConfigs && (
          // a link text to open an oidc modal that is labeled with the client_id
          <a onClick={() => props.configureOidcClient(props.action)} className="clickable">
            {clientId || 'Configure...'}
          </a>
        )}
        <span style={{ whiteSpace: 'pre' }}>
          <em> and then</em>
        </span>
      </div>
    );
  }

  return null;
};

const RuleActions = (props: {
  ruleIndex?: number;
  actions: IListenerAction[];
  listener: IListenerDescription;
  authenticateRuleToggle: (listener: IListenerDescription, index: number) => void;
  removeRule?: (listener: IListenerDescription, index: number) => void;
}) => {
  const hasAuth = Boolean(props.actions.find((a) => a.type === 'authenticate-oidc'));
  const allowAuth = props.listener.protocol === 'HTTPS';
  const tooltip = hasAuth ? 'Remove authentication from rule' : 'Authenticate rule';
  const icon = hasAuth ? 'fas fa-fw fa-lock-open' : 'fas fa-fw fa-user-lock';
  return (
    <span>
      {allowAuth && (
        <>
          <a
            className="btn btn-sm btn-link clickable"
            onClick={() => props.authenticateRuleToggle(props.listener, props.ruleIndex)}
            style={{ padding: '0' }}
          >
            <Tooltip value={tooltip}>
              <i className={icon} />
            </Tooltip>
          </a>
          <HelpField id="aws.loadBalancer.oidcAuthentication" />
        </>
      )}
      {props.ruleIndex !== undefined && props.ruleIndex >= 0 && props.removeRule && (
        <a
          className="btn btn-sm btn-link clickable"
          onClick={() => props.removeRule(props.listener, props.ruleIndex)}
          style={{ padding: '0' }}
        >
          <Tooltip value="Remove Rule">
            <i className="far fa-fw fa-trash-alt" />
          </Tooltip>
        </a>
      )}
    </span>
  );
};

interface IRulesProps {
  addRule: (listener: IListenerDescription) => void;
  authenticateRuleToggle: (listener: IListenerDescription, index: number) => void;
  removeRule: (listener: IListenerDescription, index: number) => void;
  handleRuleActionTargetChanged: (action: IListenerAction, newTarget: string) => void;
  handleRuleActionTypeChanged: (action: IListenerAction, type: string) => void;
  addCondition: (rule: IListenerRule) => void;
  removeCondition: (rule: IListenerRule, index: number) => void;
  handleConditionFieldChanged: (condition: IListenerRuleCondition, newField: ListenerRuleConditionField) => void;
  handleConditionValueChanged: (condition: IListenerRuleCondition, newValue: string) => void;
  handleHttpRequestMethodChanged: (condition: IListenerRuleCondition, newValue: string, selected: boolean) => void;
  listener: IListenerDescription;
  targetGroups: IALBTargetGroupDescription[];
  oidcConfigChanged: (action: IListenerAction, config: IAuthenticateOidcActionConfig) => void;
  redirectConfigChanged: (action: IListenerAction, config: IRedirectActionConfig) => void;
  oidcConfigs: IAuthenticateOidcActionConfig[];
  configureOidcClient: (action: IListenerAction) => void;
  configureRedirect: (action: IListenerAction) => void;
}

const Rules = SortableContainer((props: IRulesProps) => (
  <tbody>
    <tr className="not-sortable">
      <td />
      <td>Default</td>
      <td>
        {props.listener.defaultActions.map((action, index) => (
          <Action
            key={index}
            action={action}
            actionTypeChanged={(type) => props.handleRuleActionTypeChanged(action, type)}
            targetChanged={(target) => props.handleRuleActionTargetChanged(action, target)}
            targetGroups={props.targetGroups}
            oidcConfigs={props.oidcConfigs}
            oidcConfigChanged={(config) => props.oidcConfigChanged(action, config)}
            redirectConfigChanged={(config) => props.redirectConfigChanged(action, config)}
            configureOidcClient={props.configureOidcClient}
            configureRedirect={props.configureRedirect}
          />
        ))}
      </td>
      <td>
        <RuleActions
          listener={props.listener}
          actions={props.listener.defaultActions}
          authenticateRuleToggle={props.authenticateRuleToggle}
        />
      </td>
    </tr>
    {props.listener.rules
      .sort((a, b) => (a.priority as number) - (b.priority as number))
      .map((rule, index) => (
        <Rule
          key={index}
          rule={rule}
          addCondition={props.addCondition}
          handleConditionFieldChanged={props.handleConditionFieldChanged}
          handleConditionValueChanged={props.handleConditionValueChanged}
          handleHttpRequestMethodChanged={props.handleHttpRequestMethodChanged}
          handleRuleActionTargetChanged={props.handleRuleActionTargetChanged}
          handleRuleActionTypeChanged={props.handleRuleActionTypeChanged}
          oidcConfigChanged={props.oidcConfigChanged}
          redirectConfigChanged={props.redirectConfigChanged}
          removeCondition={props.removeCondition}
          authenticateRuleToggle={props.authenticateRuleToggle}
          removeRule={props.removeRule}
          targetGroups={props.targetGroups}
          oidcConfigs={props.oidcConfigs}
          listener={props.listener}
          index={index}
          ruleIndex={index}
          configureOidcClient={props.configureOidcClient}
          configureRedirect={props.configureRedirect}
        />
      ))}
    <tr className="not-sortable">
      <td colSpan={5}>
        <button type="button" className="add-new col-md-12" onClick={() => props.addRule(props.listener)}>
          <span>
            <span className="glyphicon glyphicon-plus-sign" /> Add new rule
          </span>
        </button>
      </td>
    </tr>
  </tbody>
));
