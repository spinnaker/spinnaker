import * as React from 'react';

import { Modal } from 'react-bootstrap';
import { cloneDeep, partition } from 'lodash';

import { Application, HelpField, NgReact, TaskExecutor, TaskMonitor } from '@spinnaker/core';

import { metricOptions } from './metricOptions';
import { IClusterConfig, IClusterConfigExpression } from './configBin.reader';
import { CustomMetric } from './CustomMetric';

export interface IConfigBinModalProps {
  application: Application;
  config: IClusterConfig;
  showCallback: () => void;
  clusterName: string;
  awsAccountId: string;
  region: string;
  env: string;
}

export interface IConfigBinModalState {
  taskMonitor: TaskMonitor;
  nonEditableExpressions: IClusterConfigExpression[];
  cannedExpressions: IExpressionModel[]; // preconfigured expressions
  customExpressions: IExpressionModel[];
  saving: boolean;
  saveError: string;
}

export interface IExpressionModel extends IClusterConfigExpression {
  region?: string;
  metric?: string;
  env?: string;
  isCustom?: boolean;
  index?: number;
}

export class ConfigBinModal extends React.Component<IConfigBinModalProps, IConfigBinModalState> {
  public static defaultProps: Partial<IConfigBinModalProps> = {
    config: {
      expressions: [],
      email: null,
    },
  };

  constructor(props: IConfigBinModalProps) {
    super(props);
    // only want to touch expressions in this account; will retain others to add back on save
    const allExpressions = props.config.expressions;

    const [editableExpressions, nonEditableExpressions] = partition(
      allExpressions,
      e => e.account === props.awsAccountId,
    );

    const customExpressions: IClusterConfigExpression[] = [];
    const cannedExpressions: IExpressionModel[] = [];

    editableExpressions.forEach(e => {
      const model = this.asConfigModel(e);
      if (model.region === props.region || model.isCustom) {
        if (model.isCustom) {
          customExpressions.push(model);
        } else {
          cannedExpressions.push(model);
        }
      } else {
        nonEditableExpressions.push(e);
      }
    });

    this.state = {
      taskMonitor: null,
      cannedExpressions,
      nonEditableExpressions,
      customExpressions,
      saving: false,
      saveError: null,
    };
  }

  private asConfigExpression(model: IExpressionModel): IClusterConfigExpression {
    const expression = cloneDeep(model);
    if (!model.isCustom) {
      expression.atlasUri = `http://atlas-main.${model.region}.${model.env}.netflix.net/api/v1/graph?q=name,${
        model.metric
      },:eq,nf.cluster,${this.props.clusterName},:eq,:and,:sum,(,nf.asg,),:by`;
    }
    expression.comment = 'Created via Spinnaker';
    // might consider leaving these fields and using that to drive the custom/not-custom behavior
    delete expression.isCustom;
    delete expression.region;
    delete expression.metric;
    delete expression.env;
    return expression;
  }

  private asConfigModel(expression: IClusterConfigExpression): IExpressionModel {
    const model: IExpressionModel = { isCustom: false, ...expression };
    // pretty gross, but let's take our best guess at whether these are canned expressions by parsing the URL
    // e.g. http://atlas-main.us-east-1.prod.netflix.net/api/v1/graph?q=name,cgroup.cpu.processingTime,:eq,nf.cluster,cbmigrate-titus-autoscale2,:eq,:and,:sum,(,nf.asg,),:by
    let uri = expression.atlasUri;
    if (!uri.startsWith('http://atlas-main.') || !uri.endsWith(',:eq,:and,:sum,(,nf.asg,),:by')) {
      model.isCustom = true;
      return model;
    }
    uri = uri.replace('http://atlas-main.', '').replace(',:eq,:and,:sum,(,nf.asg,),:by', '');
    const parts = uri.split('/');
    // should now have [ us-east-1.prod.netflix.net, api, v1, (graph...) ]
    const [region, env] = parts[0].split('.');
    if (!region || !env || parts.length !== 4) {
      model.isCustom = true;
      return model;
    }
    // graph?q=name,cgroup.cpu.processingTime,:eq,nf.cluster,cbmigrate-titus-autoscale2,:eq,:and,:sum,(,nf.asg,),:by
    const [, metric, , , cluster] = parts[3].split(',');
    if (!cluster || !metric || cluster !== this.props.clusterName) {
      model.isCustom = true;
      return model;
    }

    model.region = region;
    model.env = env;
    model.metric = metric;

    return model;
  }

  private close = (): void => {
    this.props.showCallback();
  };

  private save = (): void => {
    const { config, clusterName, application } = this.props;
    const { cannedExpressions, nonEditableExpressions, customExpressions } = this.state;
    const command = cloneDeep(config);
    command.expressions = nonEditableExpressions.concat(
      cannedExpressions.concat(customExpressions).map(e => this.asConfigExpression(e)),
    );

    const submitMethod = () => {
      const promise = TaskExecutor.executeTask({
        application,
        description: `Update Config Bin metric forwarding for ${clusterName}`,
        job: [
          {
            type: 'upsertConfigBin',
            cluster: clusterName,
            config: command,
          },
        ],
      });
      const done = () => this.setState({ saving: false });
      promise.then(done, done);
      return promise;
    };

    const taskMonitor = new TaskMonitor({
      application,
      title: `Update Config Bin metric forwarding`,
      modalInstance: TaskMonitor.modalInstanceEmulation(() => this.close()),
    });

    taskMonitor.submit(submitMethod);

    this.setState({ saving: true, saveError: null, taskMonitor });
  };

  private getExpressionTemplate(): IExpressionModel {
    return {
      account: this.props.awsAccountId,
      env: this.props.env,
      atlasUri: null,
      metricName: null,
      dimensions: [
        {
          name: 'AutoScalingGroupName',
          value: '$(nf.asg)',
        },
      ],
    };
  }

  private addCustomExpression = (): void => {
    const newExpression = this.getExpressionTemplate();
    newExpression.atlasUri = '';
    newExpression.metricName = '';
    newExpression.isCustom = true;
    this.setState({ customExpressions: this.state.customExpressions.concat([newExpression]) });
  };

  private removeCustomExpression = (metric: IClusterConfigExpression): void => {
    const { customExpressions } = this.state;
    this.setState({ customExpressions: customExpressions.filter(e => e !== metric) });
  };

  private metricUpdated = (oldMetric: IClusterConfigExpression, newMetric: IClusterConfigExpression): void => {
    Object.assign(oldMetric, newMetric);
  };

  private optionToggled = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const metric = event.target.value;
    const existingExpression = this.state.cannedExpressions.find(e => e.metric === metric);
    if (existingExpression) {
      this.setState({ cannedExpressions: this.state.cannedExpressions.filter(e => e !== existingExpression) });
    } else {
      const newExpression = this.getExpressionTemplate();
      newExpression.metric = metric;
      newExpression.metricName = metric;
      newExpression.region = this.props.region;
      this.setState({ cannedExpressions: this.state.cannedExpressions.concat([newExpression]) });
    }
  };

  public render() {
    const { cannedExpressions, customExpressions } = this.state;
    const { TaskMonitorWrapper } = NgReact;
    return (
      <Modal show={true} onHide={this.close}>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />

        <Modal.Header closeButton={true}>
          <h3>Edit Metrics</h3>
        </Modal.Header>
        <Modal.Body>
          <p>
            Metrics must be forwarded from Atlas to Cloudwatch in order to use them in scaling policies. Metrics can be
            forwarded via the{' '}
            <a href="http://insight-docs.prod.netflix.net/atlas/autoscaling/#sending-custom-metrics" target="_blank">
              Atlas Java Client
            </a>, or via ConfigBin, which can be configured{' '}
            <a href="https://configbin.prod.netflix.net/app/cloudwatch-forwarding/type/clusters/LATEST" target="_blank">
              here
            </a>.
          </p>
          <p>
            Additional information on metrics below can be found in{' '}
            <a href="http://insight-docs.prod.netflix.net/glossary/cgroup-system/" target="_blank">
              the documentation
            </a>.
          </p>
          <div>
            {metricOptions.map(group => {
              return (
                <div key={group.category}>
                  <h4 style={{ marginTop: '20px' }}>Available Metrics: {group.category}</h4>
                  {group.options.map(o => {
                    const enabled = cannedExpressions.some(e => e.metric === o.name);
                    return (
                      <div key={o.name} className="checkbox">
                        <label>
                          <input type="checkbox" checked={enabled} value={o.name} onChange={this.optionToggled} />
                          {o.name} <HelpField content={o.description} />
                        </label>
                      </div>
                    );
                  })}
                </div>
              );
            })}
          </div>
          <h4 style={{ marginTop: '20px' }}>Custom Metrics</h4>
          <p>If you need to scale on some other metrics, you can add them here.</p>
          {customExpressions.map((e, i) => {
            return (
              <CustomMetric
                key={i}
                metric={e}
                metricUpdated={this.metricUpdated}
                metricRemoved={this.removeCustomExpression}
              />
            );
          })}
          <button className="add-new btn btn-block btn-sm" onClick={this.addCustomExpression}>
            <span className="glyphicon glyphicon-plus-sign" />
            Add custom expression
          </button>
        </Modal.Body>
        <Modal.Footer>
          <button className="btn btn-default" onClick={this.close}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={this.save}>
            Update
          </button>
        </Modal.Footer>
      </Modal>
    );
  }
}
