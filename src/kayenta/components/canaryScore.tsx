import * as React from 'react';
import * as classNames from 'classnames';
import { ScoreClassificationLabel } from '../domain/ScoreClassificationLabel';

import { getHealthLabel } from '../service/canaryRun.service';

import './canaryScore.component.less';

export interface ICanaryScoreProps {
  score: number | string;
  health?: string;
  result: string;
  inverse: boolean;
  className?: string;
  classification?: ScoreClassificationLabel;
}

export interface ICanaryScoreState {
  score: number | string;
  healthLabel: string;
}

export class CanaryScore extends React.Component<ICanaryScoreProps, ICanaryScoreState> {
  public static defaultProps: Partial<ICanaryScoreProps> = {
    health: ''
  };

  constructor(props: ICanaryScoreProps) {
    super(props);
    this.state = this.getLabelState(props);
  }

  private getLabelState(props: ICanaryScoreProps): ICanaryScoreState {
    const score = (props.score === 0 || (props.score && props.score > 0)) ? props.score : 'N/A';

    return {
      healthLabel: getHealthLabel(props.health, props.result),
      score: score
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
      'score-label',
      'label-default',
      `label-${this.state.healthLabel}`
    ].join(' ');
    return (
      <span className={classNames(className, this.props.className)}>
        {this.state.score}
        {this.props.classification && (
          <span className="score-classification">{this.props.classification.toString()}</span>
        )}
      </span>
    );
  }
}
