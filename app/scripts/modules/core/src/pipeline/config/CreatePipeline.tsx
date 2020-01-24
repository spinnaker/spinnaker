import React from 'react';
import ReactGA from 'react-ga';
import { get } from 'lodash';
import { Dropdown } from 'react-bootstrap';

import { Application } from 'core/application';
import { IPipeline } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation/Tooltip';

import { CreatePipelineButton } from 'core/pipeline/create/CreatePipelineButton';

export interface ICreatePipelineProps {
  application: Application;
}

export class CreatePipeline extends React.Component<ICreatePipelineProps> {
  private dropdownToggled = (): void => {
    ReactGA.event({ category: 'Pipelines', action: 'Configure (top level)' });
  };

  public render() {
    const { application } = this.props;

    const pipelineConfigs = get(application, 'pipelineConfigs.data', []);
    const hasPipelineConfigs = pipelineConfigs.length > 0;

    const hasStrategyConfigs = get(application, 'strategyConfigs.data', []).length > 0;
    const header = !(hasPipelineConfigs || hasStrategyConfigs) ? (
      <li className="dropdown-header" style={{ marginTop: 0 }}>
        None yet, click <span style={{ marginLeft: '2px' }} className="glyphicon glyphicon-plus-sign" /> Create
      </li>
    ) : hasPipelineConfigs ? (
      <li className="dropdown-header" style={{ marginTop: 0 }}>
        PIPELINES
      </li>
    ) : hasStrategyConfigs ? (
      <li className="dropdown-header" style={{ marginTop: 0 }}>
        DEPLOYMENT STRATEGIES
      </li>
    ) : null;

    return (
      <Dropdown
        className="dropdown"
        id="create-pipeline-dropdown"
        style={{ marginRight: '5px' }}
        onToggle={this.dropdownToggled}
      >
        <CreatePipelineButton application={this.props.application} />
        <Dropdown.Toggle className="btn btn-sm btn-default dropdown-toggle">
          <span className="visible-xl-inline">
            <span className="glyphicon glyphicon-cog" /> Configure
          </span>
          <Tooltip value="Configure pipelines">
            <span className="hidden-xl-inline">
              <span className="glyphicon glyphicon-cog" />
            </span>
          </Tooltip>
        </Dropdown.Toggle>
        <Dropdown.Menu className="dropdown-menu">
          {header}
          {pipelineConfigs.map((pipeline: IPipeline) => (
            <Pipeline key={pipeline.id} pipeline={pipeline} type="pipeline" />
          ))}
          {hasStrategyConfigs &&
            application.strategyConfigs.data.map((pipeline: any) => (
              <Pipeline key={pipeline.id} pipeline={pipeline} type="strategy" />
            ))}
        </Dropdown.Menu>
      </Dropdown>
    );
  }
}

const Pipeline = (props: { pipeline: any; type: 'pipeline' | 'strategy' }): JSX.Element => {
  const clicked = () => {
    ReactGA.event({ category: 'Pipelines', action: `Configure ${props.type} (via top level)` });
    const { $state } = ReactInjector;
    if (!$state.current.name.includes('.executions.execution')) {
      $state.go('^.pipelineConfig', { pipelineId: props.pipeline.id });
    } else {
      $state.go('^.^.pipelineConfig', { pipelineId: props.pipeline.id });
    }
  };
  return (
    <li>
      <a onClick={clicked}>
        {props.pipeline.name} {props.pipeline.disabled && props.type === 'pipeline' && <span>(disabled)</span>}
      </a>
    </li>
  );
};
