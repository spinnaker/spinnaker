import * as React from 'react';

import { ICanaryJudgeGroupScore } from '../domain/ICanaryJudgeResult';

interface IGroupScoreProps {
  group: ICanaryJudgeGroupScore;
}

/*
* Renders an individual group score.
* */
export default ({ group }: IGroupScoreProps) => (
  <section>
    {group.name} | {group.score}
  </section>
);
