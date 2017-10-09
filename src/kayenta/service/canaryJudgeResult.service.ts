import { ReactInjector } from '@spinnaker/core';

import { ICanaryJudgeResultSummary, ICanaryJudgeResult } from 'kayenta/domain/index';

export const getCanaryJudgeResultSummaries = (): Promise<ICanaryJudgeResultSummary[]> =>
  ReactInjector.API.one('v2/canaries/canaryJudgeResult').get();

export const getCanaryJudgeResultById = (id: string): Promise<ICanaryJudgeResult> =>
  ReactInjector.API.one('v2/canaries/canaryJudgeResult').one(id).get();
