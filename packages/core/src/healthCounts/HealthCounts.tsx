import { isEqual } from 'lodash';
import React from 'react';

import { IInstanceCounts } from '../domain';
import { Placement, Tooltip } from '../presentation';

import './healthCounts.less';

export interface IHealthCountsProps {
  className?: string;
  container: IInstanceCounts;
  additionalLegendText?: string;
  legendPlacement?: Placement;
}

export interface IHealthCountsState {
  percentLabel: string;
  statusClass: string;
  total: number;
}

export class HealthCounts extends React.Component<IHealthCountsProps, IHealthCountsState> {
  public static defaultProps: Partial<IHealthCountsProps> = {
    legendPlacement: 'top',
    container: {} as IInstanceCounts,
  };

  constructor(props: IHealthCountsProps) {
    super(props);
    this.state = this.calculatePercent(props.container);
  }

  private calculatePercent(container: IInstanceCounts): IHealthCountsState {
    container = container || ({} as IInstanceCounts);

    const up = container.up || 0;
    const down = container.down || 0;
    const succeeded = container.succeeded || 0;
    const failed = container.failed || 0;
    const unknown = container.unknown || 0;
    const starting = container.starting || 0;
    const total = container.total || up + down + unknown + starting + succeeded + failed;
    const percent = total ? Math.floor(((up + succeeded) * 100) / total) : undefined;
    const percentLabel = percent === undefined ? 'n/a' : percent + '%';

    const statusClass =
      percent === undefined
        ? 'disabled'
        : percent === 100
        ? 'healthy'
        : percent < 100 && percent > 0
        ? 'unhealthy'
        : percent === 0
        ? 'dead'
        : 'disabled';

    return { percentLabel, statusClass, total };
  }

  public componentWillReceiveProps(nextProps: IHealthCountsProps): void {
    if (!isEqual(nextProps.container, this.props.container)) {
      this.setState(this.calculatePercent(nextProps.container));
    }
  }

  public render(): React.ReactElement<HealthCounts> {
    const legend = (
      <span>
        <table className="tooltip-table">
          <tbody>
            <tr>
              <td>
                <span className="glyphicon glyphicon-Up-triangle healthy" />
              </td>
              <td>Up</td>
            </tr>
            <tr>
              <td>
                <span className="glyphicon glyphicon-Down-triangle dead" />
              </td>
              <td>Down</td>
            </tr>
            <tr>
              <td>
                <span className="glyphicon glyphicon-Unknown-triangle unknown" />
              </td>
              <td>In transition or no status reported</td>
            </tr>
            <tr>
              <td>
                <span className="glyphicon glyphicon-minus disabled small" />
              </td>
              <td>Out of Service</td>
            </tr>
            <tr>
              <td>
                <span className="glyphicon glyphicon-Succeeded-triangle small" />
              </td>
              <td>Terminated successfully</td>
            </tr>
            <tr>
              <td>
                <span className="glyphicon glyphicon-Failed-triangle small" />
              </td>
              <td>Terminated unsuccessfully</td>
            </tr>
          </tbody>
        </table>
        <span>{this.props.additionalLegendText}</span>
      </span>
    );

    const container = this.props.container;
    const percentLabel = this.state.percentLabel;

    let hasValue = false;
    const counts: Array<React.ReactElement<HTMLElement>> = [];
    if (container.up) {
      counts.push(
        <span key="up">
          {' '}
          {container.up} <span className="glyphicon glyphicon-Up-triangle healthy" />
        </span>,
      );
      hasValue = true;
    }
    if (container.down && container.down !== container.missingHealthCount) {
      if (hasValue) {
        counts.push(<span key="downslash"> / </span>);
      }
      counts.push(
        <span key="down">
          {' '}
          {container.down} <span className="glyphicon glyphicon-Down-triangle dead" />
        </span>,
      );
      hasValue = true;
    }
    if (container.unknown || container.starting) {
      if (hasValue) {
        counts.push(<span key="unknownslash"> / </span>);
      }
      counts.push(
        <span key="unknown">
          {' '}
          {container.unknown + container.starting} <span className="glyphicon glyphicon-Unknown-triangle unknown" />
        </span>,
      );
      hasValue = true;
    }
    if (container.outOfService) {
      if (hasValue) {
        counts.push(<span key="outOfServiceslash"> / </span>);
      }
      counts.push(
        <span key="outOfService">
          {' '}
          {container.outOfService} <span className="glyphicon glyphicon-OutOfService-triangle disabled small" />
        </span>,
      );
      hasValue = true;
    }
    if (container.succeeded) {
      if (hasValue) {
        counts.push(<span key="succeededslash"> / </span>);
      }
      counts.push(
        <span key="succeeded">
          {' '}
          {container.succeeded} <span className="glyphicon glyphicon-Succeeded-triangle disabled small" />
        </span>,
      );
      hasValue = true;
    }
    if (container.failed) {
      if (hasValue) {
        counts.push(<span key="failedslash"> / </span>);
      }
      counts.push(
        <span key="failed">
          {' '}
          {container.failed} <span className="glyphicon glyphicon-Failed-triangle disabled small" />
        </span>,
      );
    }

    const className = this.props.className || '';

    if (percentLabel !== 'n/a') {
      return (
        <span className={`health-counts ${className}`}>
          <Tooltip template={legend} placement={this.props.legendPlacement}>
            <span className="counter instance-health-counts">
              {counts}
              {container.unknown !== this.state.total && (
                <span>
                  {' '}
                  : <span className={this.state.statusClass}>{percentLabel}</span>
                </span>
              )}
            </span>
          </Tooltip>
        </span>
      );
    } else if (container.outOfService) {
      return (
        <span className={`health-counts ${className}`}>
          <Tooltip template={legend}>
            <span className="counter instance-health-counts">
              <span>
                {container.outOfService} <span className="glyphicon glyphicon-minus disabled small" />
              </span>
            </span>
          </Tooltip>
        </span>
      );
    } else {
      return null;
    }
  }
}
