import { ReactInjector } from '@spinnaker/core';

import { ICanaryJudgeResultSummary, ICanaryJudgeResult } from 'kayenta/domain/index';

const { API } = ReactInjector;

export const getCanaryJudgeResultSummaries = (): Promise<ICanaryJudgeResultSummary[]> =>
  API.one('v2/canaries/canaryJudgeResult').get();

export const getCanaryJudgeResultById = (id: string): Promise<ICanaryJudgeResult> =>
  API.one('v2/canaries/canaryJudgeResult').one(id).get();
