import * as React from 'react';
import { ICanaryJudgeScore } from '../domain/ICanaryJudgeResult';
import * as classNames from 'classnames';

export interface ICanaryJudgeScoreProps {
  score: ICanaryJudgeScore;
  onClick: () => void;
  className: string;
}

/*
* Renders top-level canary report score.
*/
export default ({ score, className, onClick }: ICanaryJudgeScoreProps) => (
  <section className={classNames(className, 'clickable')} onClick={onClick}>
    {score.score}
  </section>
);
