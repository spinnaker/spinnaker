import * as React from 'react';

import { Modal } from 'react-bootstrap';
import { cloneDeep, partition } from 'lodash';

import { Application, NgReact, TaskExecutor, TaskMonitor } from '@spinnaker/core';

import { IMetricOption } from './metricOptions';
import { IClusterConfig, IClusterConfigExpression } from './configBin.reader';
import { CustomMetric } from './CustomMetric';

export interface IConfigBinModalProps {
  application: Application;
  config: IClusterConfig;
  cannedMetrics: IMetricOption[];
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

    const [editableExpressions, nonEditableExpressions] = partition(allExpressions, e =>
      [props.awsAccountId, '$(nf.account)'].includes(e.account),
    );

    const customExpressions: IClusterConfigExpression[] = [];
    const cannedExpressions: IExpressionModel[] = [];

    editableExpressions.forEach(e => {
      const model = this.asConfigModel(e);
      if (model.region === props.region) {
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

  private buildAtlasUri(metric: string, region: string, env: string): string {
    return `http://atlas-main.${region}.${env}.netflix.net/api/v1/graph?q=name,${metric},:eq,nf.cluster,${
      this.props.clusterName
    },:eq,:and,:sum,(,nf.account,nf.asg,),:by`;
  }

  private asConfigExpression(model: IExpressionModel): IClusterConfigExpression {
    const expression = cloneDeep(model);
    if (!model.isCustom) {
      expression.atlasUri = this.buildAtlasUri(model.metric, model.region, model.env);
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
    const uri = expression.atlasUri;
    const cannedMatch = this.props.cannedMetrics.find(
      m =>
        this.buildAtlasUri(model.metric, model.region, model.env) ===
        this.buildAtlasUri(m.metric, model.region, model.env),
    );
    model.isCustom = !cannedMatch;
    if (model.isCustom && !uri.startsWith('http://atlas-main')) {
      // not what we are expecting
      return model;
    }
    const [, region, env] = uri.split('.');
    model.region = region;
    model.env = env;
    model.metric = !model.isCustom && cannedMatch.metric;

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
      account: '$(nf.account)',
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
    const { cannedExpressions } = this.state;
    const metricName = event.target.value;
    const existingExpression = cannedExpressions.find(e => e.metricName === metricName);
    if (existingExpression) {
      this.setState({ cannedExpressions: cannedExpressions.filter(e => e !== existingExpression) });
    } else {
      const newExpression = this.getExpressionTemplate();
      const cannedMetric = this.props.cannedMetrics.find(e => e.metricName === metricName);
      newExpression.metric = cannedMetric.metric;
      newExpression.metricName = cannedMetric.metricName;
      newExpression.region = this.props.region;
      this.setState({ cannedExpressions: cannedExpressions.concat([newExpression]) });
    }
  };

  public render() {
    const { cannedMetrics = [] } = this.props;
    const { cannedExpressions, customExpressions } = this.state;
    const { TaskMonitorWrapper } = NgReact;
    return (
      <Modal show={true} onHide={this.close}>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />

        <Modal.Header closeButton={true}>
          <h3>Edit Metrics</h3>
        </Modal.Header>
        <Modal.Body>
          <p>This sets up metrics via Self-service CloudWatch Forwarding, which can be configured here.</p>
          <p>Forwarding metrics via the Atlas Java Client will be deprecated in the future.</p>
          <div>
            <h4 className="section-heading" style={{ marginTop: '10px' }}>
              Standard Metrics
            </h4>
            <div className="section-body sp-margin-m-xaxis">
              <p>These Atlas metrics are generally good ones to pick for autoscaling.</p>
              {cannedMetrics.map(metric => {
                const enabled = cannedExpressions.some(e => e.metricName === metric.metricName);
                return (
                  <div key={metric.metric} className="checkbox">
                    <label>
                      <input
                        type="checkbox"
                        checked={enabled}
                        value={metric.metricName}
                        onChange={this.optionToggled}
                      />
                      <div>
                        <b>{metric.metricName}</b>
                      </div>
                      <em>{metric.description}</em>
                    </label>
                  </div>
                );
              })}
            </div>
          </div>
          <h4 className="section-heading" style={{ marginTop: '10px' }}>
            Custom Metrics
          </h4>
          <div className="section-body sp-margin-m-xaxis">
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
              <span className="glyphicon glyphicon-plus-sign" /> Add custom expression
            </button>
          </div>
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
