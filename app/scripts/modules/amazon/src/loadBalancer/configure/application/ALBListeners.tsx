import * as React from 'react';
import Select, { Option } from 'react-select';
import { SortableContainer, SortableElement, SortableHandle, arrayMove, SortEnd } from 'react-sortable-hoc';
import { difference, flatten, get, uniq } from 'lodash';
import { BindAll } from 'lodash-decorators';
import { FormikErrors, FormikProps } from 'formik';

import { HelpField, IWizardPageProps, Tooltip, ValidationError, wizardPage } from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { AwsReactInjector } from 'amazon/reactShims';
import {
  ALBListenerProtocol,
  IALBListenerCertificate,
  IALBListenerDescription,
  IALBTargetGroupDescription,
  IAmazonApplicationLoadBalancerUpsertCommand,
  IListenerRule,
  IListenerRuleCondition,
  ListenerRuleConditionField
} from 'amazon/domain';
import { IAmazonCertificate } from 'amazon/certificates/amazon.certificate.read.service';

export interface IALBListenersState {
  certificates: { [accountId: number]: IAmazonCertificate[] };
  certificateTypes: string[];
}

const DragHandle = SortableHandle(() => (
  <span className="pipeline-drag-handle clickable glyphicon glyphicon-resize-vertical"/>
));

@BindAll()
class ALBListenersImpl extends React.Component<IWizardPageProps & FormikProps<IAmazonApplicationLoadBalancerUpsertCommand>, IALBListenersState> {
  public static LABEL = 'Listeners';
  public protocols = ['HTTP', 'HTTPS'];
  public secureProtocols = ['HTTPS', 'SSL'];

  constructor(props: IWizardPageProps & FormikProps<IAmazonApplicationLoadBalancerUpsertCommand>) {
    super(props);
    this.state = {
      certificates: [],
      certificateTypes: get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']),
    };
  }

  private getAllTargetGroupsFromListeners(listeners: IALBListenerDescription[]): string[] {
    const actions = flatten(listeners.map((l) => l.defaultActions));
    const rules = flatten(listeners.map((l) => l.rules));
    actions.push(...flatten(rules.map((r) => r.actions)));
    return uniq(actions.map((a) => a.targetGroupName));
  }

  public validate(values: IAmazonApplicationLoadBalancerUpsertCommand): FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand> {
    const errors = {} as FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand>;

    // Check to make sure all target groups have an associated listener
    const targetGroupNames = values.targetGroups.map((tg) => tg.name);
    const usedTargetGroupNames = this.getAllTargetGroupsFromListeners(values.listeners);
    const unusedTargetGroupNames = difference(targetGroupNames, usedTargetGroupNames);
    if (unusedTargetGroupNames.length === 1) {
      errors.listeners = `Target group ${unusedTargetGroupNames[0]} is unused.`;
    } else if (unusedTargetGroupNames.length > 1) {
      errors.listeners = `Target groups ${unusedTargetGroupNames.join(', ')} are unused.`;
    }

    const missingRuleFields = values.listeners.find((l) => {
      const defaultActionsHaveMissingTarget = !!l.defaultActions.find((da) => !da.targetGroupName || da.targetGroupName === '');
      const rulesHaveMissingFields = !!l.rules.find((rule) => {
        const missingTargets = !!rule.actions.find((a) => !a.targetGroupName || a.targetGroupName === '');
        const missingValue = !!rule.conditions.find((c) => c.values.includes(''));
        return missingTargets || missingValue;
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
  }

  private loadCertificates(): void {
    AwsReactInjector.amazonCertificateReader.listCertificates().then((certificates) => {
      this.setState({ certificates });
    });
  }

  private updateListeners(): void {
    this.props.setFieldValue('listeners', this.props.values.listeners);
  }

  private needsCert(): boolean {
    return this.props.values.listeners.some((listener) => listener.protocol === 'HTTPS');
  }

  private showCertificateSelect(certificate: IALBListenerCertificate): boolean {
    return certificate.type === 'iam' && this.state.certificates && Object.keys(this.state.certificates).length > 0;
  }

  private addListenerCertificate(listener: IALBListenerDescription): void {
    listener.certificates = listener.certificates || [];
    listener.certificates.push({
      certificateArn: undefined,
      type: 'iam',
      name: undefined,
    });
  }

  private listenerProtocolChanged(listener: IALBListenerDescription, newProtocol: ALBListenerProtocol): void {
    listener.protocol = newProtocol;
    if (listener.protocol === 'HTTPS') {
      listener.port = 443;
      if (!listener.certificates || listener.certificates.length === 0) {
        this.addListenerCertificate(listener);
      }
    }
    if (listener.protocol === 'HTTP') {
      listener.port = 80;
    }
    this.updateListeners();
  }

  private listenerPortChanged(listener: IALBListenerDescription, newPort: string): void {
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
    this.props.values.listeners.splice(index, 1);
    this.updateListeners();
  }

  private addListener(): void {
    this.props.values.listeners.push({
      certificates: [],
      protocol: 'HTTP',
      port: 80,
      defaultActions: [
        {
          type: 'forward',
          targetGroupName: ''
        }
      ],
      rules: [],
    });
    this.updateListeners();
  }

  private addRule(listener: IALBListenerDescription): void {
    const newRule: IListenerRule = {
      priority: null,
      actions: [{
        type: 'forward',
        targetGroupName: ''
      }],
      conditions: [{
        field: 'path-pattern',
        values: ['']
      }],
    };

    listener.rules.push(newRule);
    this.updateListeners();
  }

  public removeRule(listener: IALBListenerDescription, index: number): void {
    listener.rules.splice(index, 1);
    this.updateListeners();
  }


  private handleDefaultTargetChanged(listener: IALBListenerDescription, newTarget: string): void {
    listener.defaultActions[0].targetGroupName = newTarget;
    this.updateListeners();
  }

  private handleConditionFieldChanged(condition: IListenerRuleCondition, newField: ListenerRuleConditionField): void {
    condition.field = newField;
    this.updateListeners();
  }

  private handleConditionValueChanged(condition: IListenerRuleCondition, newValue: string): void {
    condition.values[0] = newValue;
    this.updateListeners();
  }

  private addCondition(rule: IListenerRule): void {
    if (rule.conditions.length === 1) {
      const field = rule.conditions[0].field === 'path-pattern' ? 'host-header' : 'path-pattern';
      rule.conditions.push({ field, values: [''] });
    }
    this.updateListeners();
  }

  private removeCondition(rule: IListenerRule, index: number): void {
    rule.conditions.splice(index, 1);
    this.updateListeners();
  }

  private handleRuleActionTargetChanged(rule: IListenerRule, newTarget: string): void {
    rule.actions[0].targetGroupName = newTarget;
    this.updateListeners();
  }

  private handleSortEnd(sortEnd: SortEnd, listener: IALBListenerDescription): void {
    listener.rules = arrayMove(listener.rules, sortEnd.oldIndex, sortEnd.newIndex);
    this.updateListeners();
  }

  public render() {
    const { errors, values } = this.props;
    const { certificates, certificateTypes } = this.state;

    const certificatesForAccount = certificates[values.credentials as any] || [];
    const certificateOptions = certificatesForAccount.map((cert) => { return { label: cert.serverCertificateName, value: cert.serverCertificateName } })

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
                        onChange={(event) => this.listenerProtocolChanged(listener, event.target.value as ALBListenerProtocol)}
                      >
                       {this.protocols.map((p) => <option key={p}>{p}</option>)}
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
                    <a
                      className="sm-label clickable"
                      onClick={() => this.removeListener(index)}
                    >
                      <span className="glyphicon glyphicon-trash"/>
                    </a>
                  </div>
                </div>
              </div>
              {this.needsCert() && (
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
                        {certificateTypes.map((t) => <option key={t}>{t}</option>)}
                      </select>
                      {this.showCertificateSelect(certificate) && (
                        <Select
                          wrapperStyle={{ width: '100%' }}
                          clearable={false}
                          required={true}
                          options={certificateOptions}
                          onChange={(value: Option<string>) => this.handleCertificateChanged(certificate, value.value)}
                          value={certificate.name}
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
                <div
                  className="wizard-pod-row-title"
                  style={{ height: '30px' }}
                >
                  Rules
                </div>
                <div
                  className="wizard-pod-row-contents"
                  style={{ padding: '0' }}
                >
                  <table className="table table-condensed packed rules-table">
                    <thead>
                      <tr>
                        <th style={{ width: '10px', padding: '0' }}/>
                        <th style={{ width: '226px' }}>If</th>
                        <th style={{ width: '75px' }}>Then</th>
                        <th>Target</th>
                        <th style={{ width: '30px' }}/>
                      </tr>
                    </thead>
                    <Rules
                      addCondition={this.addCondition}
                      addRule={this.addRule}
                      distance={10}
                      handleConditionFieldChanged={this.handleConditionFieldChanged}
                      handleConditionValueChanged={this.handleConditionValueChanged}
                      handleDefaultTargetChanged={this.handleDefaultTargetChanged}
                      handleRuleActionTargetChanged={this.handleRuleActionTargetChanged}
                      listener={listener}
                      helperClass="rule-sortable-helper"
                      removeRule={this.removeRule}
                      removeCondition={this.removeCondition}
                      targetGroups={values.targetGroups}
                      onSortEnd={(sortEnd) => this.handleSortEnd(sortEnd, listener)}
                    />
                  </table>
                </div>
              </div>
            </div>
          </div>
          ))}
            {errors.listeners && <div className="wizard-pod-row-errors"><ValidationError message={errors.listeners}/></div>}
            <table className="table table-condensed packed">
              <tbody>
                <tr>
                  <td>
                    <button type="button" className="add-new col-md-12" onClick={this.addListener}>
                      <span><span className="glyphicon glyphicon-plus-sign"/> Add new listener</span>
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
};

interface IRuleProps {
  rule: IListenerRule;
  listener: IALBListenerDescription;
  index: number;
  targetGroups: IALBTargetGroupDescription[];
  removeRule: (listener: IALBListenerDescription, index: number) => void;
  handleRuleActionTargetChanged: (rule: IListenerRule, newTarget: string) => void;
  addCondition: (rule: IListenerRule) => void;
  removeCondition: (rule: IListenerRule, index: number) => void;
  handleConditionFieldChanged: (condition: IListenerRuleCondition, newField: ListenerRuleConditionField) => void;
  handleConditionValueChanged: (condition: IListenerRuleCondition, newValue: string) => void;
}

const Rule = SortableElement((props: IRuleProps) => (
  <tr className="listener-rule">
    <td className="handle"><DragHandle/></td>
    <td>
      {props.rule.conditions.map((condition, cIndex) => (
        <div key={cIndex} className="listener-rule-condition">
          <select
            className="form-control input-sm inline-number"
            value={condition.field}
            onChange={(event) => props.handleConditionFieldChanged(condition, event.target.value as ListenerRuleConditionField)}
            style={{ width: '60px' }}
            required={true}
          >
            {(props.rule.conditions.length === 1 || condition.field === 'host-header') && <option label="Host" value="host-header"/>}
            {(props.rule.conditions.length === 1 || condition.field === 'path-pattern') && <option label="Path" value="path-pattern"/>}
          </select>
          {condition.field === 'path-pattern' && <HelpField id="aws.loadBalancer.ruleCondition.path"/>}
          {condition.field === 'host-header' && <HelpField id="aws.loadBalancer.ruleCondition.host"/>}
          <input
            className="form-control input-sm"
            type="text"
            value={condition.values[0]}
            onChange={(event) => props.handleConditionValueChanged(condition, event.target.value)}
            maxLength={128}
            required={true}
          />
          <span className="remove-condition">
            {cIndex === 1 && (
              <a
                className="btn btn-sm btn-link clickable"
                onClick={() => props.removeCondition(props.rule, cIndex)}
                style={{ padding: '0' }}
              >
                <Tooltip value="Remove Condition">
                  <span className="glyphicon glyphicon-trash"/>
                </Tooltip>
              </a>
            )}
          </span>
        </div>
      ))}
      {props.rule.conditions.length === 1 && (
        <div className="add-new-container">
          <button type="button" className="add-new col-md-12" onClick={() => props.addCondition(props.rule)}>
            <span><span className="glyphicon glyphicon-plus-sign"/> Add new condition</span>
          </button>
          <span style={{ minWidth: '15px' }}/>
        </div>
      )}
    </td>
    <td>forward to</td>
    <td>
      <select
        className="form-control input-sm"
        value={props.rule.actions[0].targetGroupName}
        onChange={(event) => props.handleRuleActionTargetChanged(props.rule, event.target.value)}
        required={true}
      >
        <option value=""/>
        {uniq(props.targetGroups.map((tg) => tg.name)).map((name) => <option key={name}>{name}</option>)}
      </select>
    </td>
    <td>
      <a
        className="btn btn-sm btn-link clickable"
        onClick={() => props.removeRule(props.listener, props.index)}
        style={{ padding: '0' }}
      >
        <Tooltip value="Remove Rule"><span className="glyphicon glyphicon-trash"/></Tooltip>
      </a>
    </td>
  </tr>
));

interface IRulesProps {
  addRule: (listener: IALBListenerDescription) => void;
  removeRule: (listener: IALBListenerDescription, index: number) => void;
  handleRuleActionTargetChanged: (rule: IListenerRule, newTarget: string) => void;
  addCondition: (rule: IListenerRule) => void;
  removeCondition: (rule: IListenerRule, index: number) => void;
  handleConditionFieldChanged: (condition: IListenerRuleCondition, newField: ListenerRuleConditionField) => void;
  handleConditionValueChanged: (condition: IListenerRuleCondition, newValue: string) => void;
  listener: IALBListenerDescription;
  targetGroups: IALBTargetGroupDescription[];
  handleDefaultTargetChanged: (listener: IALBListenerDescription, newTarget: string) => void;
}

const Rules = SortableContainer((props: IRulesProps) => (
  <tbody>
    <tr className="not-sortable">
      <td/>
      <td>Default</td>
      <td>forward to</td>
      <td>
        <select
          className="form-control input-sm"
          value={props.listener.defaultActions[0].targetGroupName}
          onChange={(event) => props.handleDefaultTargetChanged(props.listener, event.target.value)}
          required={true}
        >
          <option value=""/>
          {uniq(props.targetGroups.map((tg) => tg.name)).map((name) => <option key={name}>{name}</option>)}
        </select>
      </td>
      <td/>
    </tr>
    {props.listener.rules.map((rule, index) => (
      <Rule
        key={index}
        rule={rule}
        addCondition={props.addCondition}
        handleConditionFieldChanged={props.handleConditionFieldChanged}
        handleConditionValueChanged={props.handleConditionValueChanged}
        handleRuleActionTargetChanged={props.handleRuleActionTargetChanged}
        removeCondition={props.removeCondition}
        removeRule={props.removeRule}
        targetGroups={props.targetGroups}
        listener={props.listener}
        index={index}
      />
    ))}
    <tr className="not-sortable">
      <td colSpan={5}>
        <button type="button" className="add-new col-md-12" onClick={() => props.addRule(props.listener)}>
          <span><span className="glyphicon glyphicon-plus-sign"/> Add new rule</span>
        </button>
      </td>
    </tr>
  </tbody>
));

export const ALBListeners = wizardPage(ALBListenersImpl);
