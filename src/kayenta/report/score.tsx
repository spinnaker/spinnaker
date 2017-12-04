import * as React from 'react';
import * as classNames from 'classnames';
import { ICanaryJudgeScore } from '../domain/ICanaryJudgeResult';
import ClickableHeader from './clickableHeader';
import { mapScoreClassificationToColor } from './colors';

export interface ICanaryJudgeScoreProps {
  score: ICanaryJudgeScore;
  onClick: () => void;
  className?: string;
}

/*
* Renders top-level canary report score.
*/
export default ({ score, className, onClick }: ICanaryJudgeScoreProps) => (
  <section className={classNames(className, 'clickable')}>
    <ClickableHeader
      style={{
        width: '70%',
        backgroundColor: mapScoreClassificationToColor(score.classification),
        margin: '0 auto'
      }}
      onClick={onClick}
      label={score.score.toPrecision(2)}
      className={className}
    />
  </section>
);
