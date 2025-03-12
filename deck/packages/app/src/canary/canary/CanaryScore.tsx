import React from 'react';

import './canaryScore.component.less';

export interface ICanaryScoreProps {
  score: number | string;
  health?: string;
  result: string;
  inverse: boolean;
}

export interface ICanaryScoreState {
  score: number | string;
  healthLabel: string;
}

export class CanaryScore extends React.Component<ICanaryScoreProps, ICanaryScoreState> {
  public static defaultProps: Partial<ICanaryScoreProps> = {
    health: '',
  };

  constructor(props: ICanaryScoreProps) {
    super(props);
    this.state = this.getLabelState(props);
  }

  private getLabelState(props: ICanaryScoreProps): ICanaryScoreState {
    const health = (props.health || '').toLowerCase();
    const result = (props.result || '').toLowerCase();
    const score = props.score === 0 || (props.score && props.score > 0) ? props.score : 'N/A';
    const healthLabel =
      health === 'unhealthy'
        ? 'unhealthy'
        : result === 'success'
        ? 'healthy'
        : result === 'failure'
        ? 'failing'
        : 'unknown';

    return {
      healthLabel,
      score,
    };
  }

  public componentWillReceiveProps(props: ICanaryScoreProps) {
    this.setState(this.getLabelState(props));
  }

  public render() {
    const className = [
      this.props.inverse ? 'inverse' : '',
      'score',
      'label',
      'label-default',
      `label-${this.state.healthLabel}`,
    ].join(' ');
    return <span className={className}>{this.state.score}</span>;
  }
}
