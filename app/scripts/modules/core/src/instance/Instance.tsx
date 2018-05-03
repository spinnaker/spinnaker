import * as React from 'react';
import * as $ from 'jquery';
import { BindAll } from 'lodash-decorators';

import { IInstance } from 'core/domain';

export interface IInstanceProps {
  instance: IInstance;
  active: boolean;
  highlight: string;
  onInstanceClicked(instance: IInstance): void;
}

@BindAll()
export class Instance extends React.Component<IInstanceProps> {
  private $tooltipElement: JQuery = null;

  private handleClick(event: React.MouseEvent<any>) {
    event.preventDefault();
    this.props.onInstanceClicked(this.props.instance);
  }

  public onMouseOver(event: React.MouseEvent<any>) {
    this.$tooltipElement = $(event.target);
    this.$tooltipElement.tooltip({ animation: false, container: 'body' } as JQueryUI.TooltipOptions).tooltip('show');
  }

  public shouldComponentUpdate(nextProps: IInstanceProps) {
    const checkProps: Array<keyof IInstanceProps> = ['instance', 'active', 'highlight'];
    return checkProps.some(key => this.props[key] !== nextProps[key]);
  }

  public componentWillUnmount() {
    if (this.$tooltipElement) {
      this.$tooltipElement.tooltip('destroy');
    }
  }

  public render() {
    const { instance, active, highlight } = this.props;
    const { id, healthState } = instance;

    const className = `instance health-status-${healthState} ${highlight === id ? 'highlighted' : ''} ${
      active ? 'active' : ''
    }`;
    return (
      <a
        className={className}
        title={id}
        data-toggle="tooltip"
        onMouseOver={this.onMouseOver}
        onClick={this.handleClick}
      />
    );
  }
}
