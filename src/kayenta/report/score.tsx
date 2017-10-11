import * as React from 'react';
import { ICanaryJudgeScore } from '../domain/ICanaryJudgeResult';

interface ICanaryJudgeScoreProps {
  score: ICanaryJudgeScore;
  onClick: () => void;
  className: string;
}

/*
* Renders top-level canary result score.
*/
export default ({ score, className, onClick }: ICanaryJudgeScoreProps) => (
  <section className={className} onClick={onClick}>
    {score.score}
  </section>
);
