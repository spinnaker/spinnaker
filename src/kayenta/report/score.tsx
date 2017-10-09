import * as React from 'react';
import { ICanaryJudgeScore } from '../domain/ICanaryJudgeResult';

interface ICanaryJudgeScoreProps {
  score: ICanaryJudgeScore;
  className: string;
}

/*
* Renders top-level canary report score.
*/
export default ({ score, className }: ICanaryJudgeScoreProps) => (
  <section className={className}>
    {score.score}
  </section>
);
