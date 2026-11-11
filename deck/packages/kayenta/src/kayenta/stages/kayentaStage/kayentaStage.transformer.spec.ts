import type { IExecution, IExecutionStage } from '@spinnaker/core';

import { KayentaStageTransformer } from './kayentaStage.transformer';
import { KAYENTA_CANARY, RUN_CANARY, RUN_CANARY_INTERVALS } from './stageTypes';

describe('KayentaStageTransformer exceptions', () => {
  const transformer = new KayentaStageTransformer();

  const buildExecution = (
    kayentaOverrides: Partial<IExecutionStage>,
    kayentaContext: any,
    runStages: any[] = [],
  ): IExecution => {
    const kayenta: any = {
      id: 'k1',
      type: KAYENTA_CANARY,
      status: 'FAILED_CONTINUE',
      name: 'canary',
      tasks: [],
      context: {
        intervalStageId: 'interval',
        canaryConfig: { scoreThresholds: { marginal: 50, pass: 75 } },
        ...kayentaContext,
      },
      ...kayentaOverrides,
    };
    const runs = runStages.map((run, i) => ({
      id: `r${i}`,
      type: RUN_CANARY,
      parentStageId: 'interval',
      tasks: [],
      context: {},
      ...run,
    }));
    return { stages: [kayenta, ...runs] } as any;
  };

  const exceptionsOf = (execution: IExecution): string[] =>
    execution.stages.find(({ type }) => type === KAYENTA_CANARY).exceptions as any;

  const message = 'Final canary score 60 is below the pass score threshold.';

  it('includes the score message when the stage failed between marginal and pass', () => {
    const execution = buildExecution({}, { canaryScores: [60], canaryScoreMessage: message });
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([message]);
  });

  it('includes the score message when the score is at or below marginal', () => {
    const execution = buildExecution({}, { canaryScores: [20], canaryScoreMessage: message });
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([message]);
  });

  it('includes the score message for a score of 0', () => {
    const execution = buildExecution({ status: 'SUCCEEDED' }, { canaryScores: [0], canaryScoreMessage: message });
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([message]);
  });

  it('adds nothing for a passing stage', () => {
    const execution = buildExecution({ status: 'SUCCEEDED' }, { canaryScores: [90] });
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([]);
  });

  it('does not add an empty exception for a failed stage with no message', () => {
    const execution = buildExecution({}, { canaryScores: [60] });
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([]);
  });

  it('does not show a passing score message when the stage failed for another reason', () => {
    const passMessage = 'Final canary score 95.0 met or exceeded the pass score threshold.';
    const execution = buildExecution({}, { canaryScores: [95], canaryScoreMessage: passMessage });
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([]);
  });

  it('does not show neutral messages when no thresholds were specified', () => {
    const neutral = 'No score thresholds were specified.';
    const execution = buildExecution(
      {},
      {
        canaryScores: [60],
        canaryScoreMessage: neutral,
        canaryConfig: { scoreThresholds: { marginal: null, pass: null } },
      },
    );
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([]);
  });

  describe('omitted scoreThresholds object (Orca defaults to pass 75 / marginal 50)', () => {
    it('shows the message for a score below the default pass threshold', () => {
      const execution = buildExecution({}, { canaryConfig: {}, canaryScores: [60], canaryScoreMessage: message });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([message]);
    });

    it('does not show the message for a score above the default pass threshold', () => {
      const execution = buildExecution({}, { canaryConfig: {}, canaryScores: [80], canaryScoreMessage: message });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([]);
    });
  });

  it('shows the below-pass message once for a score between marginal and pass', () => {
    const belowPass = 'Final canary score 60.0 is below the pass score threshold.';
    const execution = buildExecution({}, { canaryScores: [60], canaryScoreMessage: belowPass });
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([belowPass]);
  });

  describe('string score thresholds (as saved by the stage editor)', () => {
    const stringThresholds = { canaryConfig: { scoreThresholds: { marginal: '80', pass: '90' } } };

    it('shows the message for a score below the pass threshold', () => {
      const execution = buildExecution({}, { ...stringThresholds, canaryScores: [85], canaryScoreMessage: message });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([message]);
    });

    it('shows the message for a score at or below the marginal threshold', () => {
      const execution = buildExecution({}, { ...stringThresholds, canaryScores: [40], canaryScoreMessage: message });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([message]);
    });

    it('adds nothing for a passing score on a stage that failed for another reason', () => {
      const execution = buildExecution({}, { ...stringThresholds, canaryScores: [95], canaryScoreMessage: message });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([]);
    });

    it('treats empty-string thresholds as missing', () => {
      const execution = buildExecution(
        {},
        {
          canaryConfig: { scoreThresholds: { marginal: '', pass: '' } },
          canaryScores: [0],
          canaryScoreMessage: message,
        },
      );
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([]);
    });

    it('ignores an empty marginal threshold but still applies the pass threshold', () => {
      const execution = buildExecution(
        {},
        {
          canaryConfig: { scoreThresholds: { marginal: '', pass: '75' } },
          canaryScores: [60],
          canaryScoreMessage: message,
        },
      );
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([message]);
    });
  });

  describe('only a pass threshold', () => {
    const passOnly = { canaryConfig: { scoreThresholds: { pass: 75 } } };

    it('shows the message for a score below pass', () => {
      const execution = buildExecution({}, { ...passOnly, canaryScores: [60], canaryScoreMessage: message });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([message]);
    });

    it('does not show the message for a score at or above pass', () => {
      const execution = buildExecution({}, { ...passOnly, canaryScores: [80], canaryScoreMessage: message });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([]);
    });
  });

  it('ignores the runCanary stage messages when the kayenta stage has its own', () => {
    const execution = buildExecution({}, { canaryScores: [60], canaryScoreMessage: message }, [
      {
        status: 'TERMINAL',
        context: { canaryScoreMessage: 'Canary score is not above the marginal score threshold.' },
      },
    ]);
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([message]);
  });

  it('shows the last run message exactly once when the kayenta stage has none and the score failed', () => {
    const marginalMessage = 'Canary score is not above the marginal score threshold.';
    const execution = buildExecution({}, { canaryConfig: { scoreThresholds: { marginal: 75, pass: 95 } } }, [
      { status: 'TERMINAL', context: { canaryScore: 70, canaryScoreMessage: marginalMessage } },
    ]);
    transformer.transform(null, execution);
    expect(exceptionsOf(execution)).toEqual([marginalMessage]);
  });

  describe('while the kayentaCanary stage is still running failure cleanup', () => {
    const marginalMessage = 'Canary score is not above the marginal score threshold.';

    const buildRunningExecution = (runOverrides: any = {}): IExecution => {
      const execution = buildExecution({ status: 'RUNNING' }, { intervalStageId: 'int' }, [
        {
          status: 'TERMINAL',
          parentStageId: 'int',
          context: {
            canaryScore: 40,
            scoreThresholds: { marginal: 50, pass: 75 },
            canaryScoreMessage: marginalMessage,
          },
          ...runOverrides,
        },
        { id: 'cleanupWait', type: 'wait', status: 'RUNNING', parentStageId: 'k1', context: {} },
      ]);
      (execution.stages as any[]).push({
        id: 'int',
        type: RUN_CANARY_INTERVALS,
        status: 'FAILED_CONTINUE',
        parentStageId: 'k1',
        tasks: [],
        context: {},
      });
      return execution;
    };

    it('shows a failed run message once and hides the interval stage', () => {
      const execution = buildRunningExecution();
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([marginalMessage]);
      expect(execution.stages.some(({ type }) => type === RUN_CANARY_INTERVALS)).toBe(false);
    });

    it('adds nothing when the failed run passes its own thresholds', () => {
      const execution = buildRunningExecution({
        context: { canaryScore: 90, scoreThresholds: { marginal: 50, pass: 75 }, canaryScoreMessage: marginalMessage },
      });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([]);
    });

    it('applies string thresholds on the run', () => {
      const execution = buildRunningExecution({
        context: {
          canaryScore: 40,
          scoreThresholds: { marginal: '50', pass: '75' },
          canaryScoreMessage: marginalMessage,
        },
      });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([marginalMessage]);
    });

    it('falls back to the stage thresholds when the run has none', () => {
      const execution = buildRunningExecution({ context: { canaryScore: 40, canaryScoreMessage: marginalMessage } });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([marginalMessage]);
    });

    it('ignores runs that did not fail', () => {
      const execution = buildRunningExecution({ status: 'SUCCEEDED' });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toEqual([]);
    });
  });

  describe('runCanaryIntervals stage', () => {
    const marginalMessage = 'Canary score is not above the marginal score threshold.';

    const buildIntervalExecution = (intervalOverrides: any, runOverrides: any = {}): IExecution => {
      const execution = buildExecution({ id: 'k' }, { intervalStageId: 'int' }, [
        {
          status: 'TERMINAL',
          parentStageId: 'int',
          context: { canaryScore: 40, canaryScoreMessage: marginalMessage },
          ...runOverrides,
        },
      ]);
      (execution.stages as any[]).push({
        id: 'int',
        type: RUN_CANARY_INTERVALS,
        status: 'FAILED_CONTINUE',
        parentStageId: 'k',
        tasks: [],
        context: {},
        ...intervalOverrides,
      });
      return execution;
    };

    const hasIntervalStage = (execution: IExecution) =>
      execution.stages.some(({ type }) => type === RUN_CANARY_INTERVALS);

    it('hides a failed interval stage and reports the run message exactly once', () => {
      const execution = buildIntervalExecution({});
      transformer.transform(null, execution);
      expect(hasIntervalStage(execution)).toBe(false);
      expect(exceptionsOf(execution)).toEqual([marginalMessage]);
    });

    it('surfaces an exception carried by the interval stage itself', () => {
      const execution = buildIntervalExecution({ context: { exception: { details: { responseBody: 'boom' } } } });
      transformer.transform(null, execution);
      expect(hasIntervalStage(execution)).toBe(false);
      expect(exceptionsOf(execution)).toContain('boom');
      expect(exceptionsOf(execution)).toContain(marginalMessage);
    });

    it('surfaces the failure message of an interval stage that has no response body', () => {
      const execution = buildIntervalExecution({
        context: { exception: { details: { errors: ['planning failed'] } } },
      });
      transformer.transform(null, execution);
      expect(exceptionsOf(execution)).toContain('planning failed');
    });

    it('still hides a succeeded interval stage', () => {
      const execution = buildIntervalExecution(
        { status: 'SUCCEEDED' },
        { status: 'SUCCEEDED', context: { canaryScoreMessage: marginalMessage } },
      );
      transformer.transform(null, execution);
      expect(hasIntervalStage(execution)).toBe(false);
    });

    it('does not add the interval stage to the kayenta stage tasks', () => {
      const execution = buildIntervalExecution({});
      transformer.transform(null, execution);
      const kayenta = execution.stages.find(({ type }) => type === KAYENTA_CANARY);
      expect(kayenta.tasks.some((t: any) => t.id === 'int')).toBe(false);
    });

    it('tolerates a missing interval stage', () => {
      const execution = buildIntervalExecution({});
      execution.stages = execution.stages.filter(({ id }) => id !== 'int');
      expect(() => transformer.transform(null, execution)).not.toThrow();
    });
  });
});

describe('KayentaStageTransformer overall badge', () => {
  const transformer = new KayentaStageTransformer();

  const contextOf = (status: string, context: any) => {
    const execution: any = {
      stages: [
        {
          id: 'k1',
          type: KAYENTA_CANARY,
          status,
          name: 'canary',
          tasks: [],
          context: { canaryConfig: { scoreThresholds: { marginal: 50, pass: 75 } }, ...context },
        },
      ],
    };
    transformer.transform(null, execution);
    return execution.stages[0].context;
  };

  it('marks a succeeded stage as a success', () => {
    const context = contextOf('SUCCEEDED', { canaryScores: [90] });
    expect(context.overallResult).toBe('success');
    expect(context.overallHealth).toBeUndefined();
  });

  it('marks a failed stage scoring between marginal and pass as unhealthy', () => {
    const context = contextOf('FAILED_CONTINUE', { canaryScores: [60] });
    expect(context.overallResult).toBeUndefined();
    expect(context.overallHealth).toBe('unhealthy');
  });

  it('marks a failed stage scoring 0 as unhealthy', () => {
    const context = contextOf('FAILED_CONTINUE', { canaryScores: [0] });
    expect(context.overallResult).toBeUndefined();
    expect(context.overallHealth).toBe('unhealthy');
  });

  it('marks a failed stage with no score as unhealthy', () => {
    const context = contextOf('FAILED_CONTINUE', {});
    expect(context.overallResult).toBeUndefined();
    expect(context.overallHealth).toBe('unhealthy');
  });

  it('uses the default thresholds when scoreThresholds is omitted', () => {
    expect(contextOf('FAILED_CONTINUE', { canaryConfig: {}, canaryScores: [60] }).overallHealth).toBe('unhealthy');
    expect(contextOf('FAILED_CONTINUE', { canaryConfig: {}, canaryScores: [80] }).overallResult).toBe('success');
  });

  it('marks a failed stage scoring at or above pass as a success', () => {
    const context = contextOf('FAILED_CONTINUE', { canaryScores: [75] });
    expect(context.overallResult).toBe('success');
    expect(context.overallHealth).toBeUndefined();
  });

  it('applies string thresholds', () => {
    const thresholds = { canaryConfig: { scoreThresholds: { marginal: '80', pass: '90' } } };
    expect(contextOf('FAILED_CONTINUE', { ...thresholds, canaryScores: [85] }).overallHealth).toBe('unhealthy');
    expect(contextOf('FAILED_CONTINUE', { ...thresholds, canaryScores: [95] }).overallResult).toBe('success');
  });

  it('leaves a canceled stage unmarked', () => {
    const context = contextOf('CANCELED', { canaryScores: [60] });
    expect(context.overallResult).toBeUndefined();
    expect(context.overallHealth).toBeUndefined();
  });

  it('leaves a running stage unmarked', () => {
    const context = contextOf('RUNNING', { canaryScores: [60] });
    expect(context.overallResult).toBeUndefined();
    expect(context.overallHealth).toBeUndefined();
  });
});

describe('KayentaStageTransformer scores near a threshold', () => {
  const transformer = new KayentaStageTransformer();
  const message = 'Final canary score is below the pass score threshold.';
  const thresholds = { marginal: 50, pass: 75 };

  const buildKayentaExecution = (canaryScores: any[]): any => ({
    stages: [
      {
        id: 'k1',
        type: KAYENTA_CANARY,
        status: 'FAILED_CONTINUE',
        name: 'canary',
        tasks: [],
        context: { canaryConfig: { scoreThresholds: thresholds }, canaryScores, canaryScoreMessage: message },
      },
    ],
  });

  // The kayentaCanary stage has no canaryScores, so its score comes from the last runCanary stage.
  const buildRunExecution = (canaryScore: any): any => ({
    stages: [
      {
        id: 'k1',
        type: KAYENTA_CANARY,
        status: 'FAILED_CONTINUE',
        name: 'canary',
        tasks: [],
        context: { intervalStageId: 'int', canaryConfig: { scoreThresholds: thresholds }, canaryScoreMessage: message },
      },
      {
        id: 'r0',
        type: RUN_CANARY,
        status: 'SUCCEEDED',
        parentStageId: 'int',
        tasks: [],
        context: { canaryScore, scoreThresholds: thresholds },
      },
    ],
  });

  const kayentaOf = (execution: any) => execution.stages.find(({ type }: any) => type === KAYENTA_CANARY);

  const cases = [
    { score: 74.996, shown: 75, failed: true, why: 'rounds up to pass but is below it' },
    { score: 49.996, shown: 50, failed: true, why: 'is at or below marginal' },
    { score: 50.004, shown: 50, failed: true, why: 'rounds to marginal but is above it, and below pass' },
    { score: 75, shown: 75, failed: false, why: 'is exactly at pass' },
    { score: 75.004, shown: 75, failed: false, why: 'is above pass' },
  ];

  cases.forEach(({ score, shown, failed, why }) => {
    // 50.004 is above marginal, so Orca fails it only because it is below pass.
    describe(`raw score ${score} (${why})`, () => {
      const expectKayenta = (execution: any) => {
        const stage = kayentaOf(execution);
        expect(stage.context.overallScore).toBe(shown);
        expect(stage.exceptions).toEqual(failed ? [message] : []);
        expect(stage.context.overallResult).toBe(failed ? undefined : 'success');
        expect(stage.context.overallHealth).toBe(failed ? 'unhealthy' : undefined);
      };

      it('decides on the raw score for the kayentaCanary stage and displays the rounded score', () => {
        const execution = buildKayentaExecution([score]);
        transformer.transform(null, execution);
        expectKayenta(execution);
      });

      it('decides on the raw score when falling back to the last run score', () => {
        const execution = buildRunExecution(score);
        transformer.transform(null, execution);
        expectKayenta(execution);
        expect(execution.stages.find(({ id }: any) => id === 'r0')).toBeUndefined();
      });

      it('gives the same result when transformed twice', () => {
        const execution = buildKayentaExecution([score]);
        transformer.transform(null, execution);
        transformer.transform(null, execution);
        expectKayenta(execution);
      });

      it('gives the same result when the same stage objects are transformed again (run fallback)', () => {
        const execution = buildRunExecution(score);
        const [kayenta, run] = execution.stages;
        transformer.transform(null, execution);
        const again: any = { stages: [kayenta, run] };
        transformer.transform(null, again);
        expectKayenta(again);
        expect(run.context.canaryScore).toBe(shown);
      });
    });
  });

  it('marks a succeeded run as a success only when its raw score reaches pass, even on a re-transform', () => {
    const below = buildRunExecution(74.996);
    const [belowKayenta, belowRun] = below.stages;
    transformer.transform(null, below);
    transformer.transform(null, { stages: [belowKayenta, belowRun] });
    expect(belowRun.result).toBeUndefined();
    expect(belowRun.context.canaryScore).toBe(75);

    const above = buildRunExecution(75.004);
    const [aboveKayenta, aboveRun] = above.stages;
    transformer.transform(null, above);
    transformer.transform(null, { stages: [aboveKayenta, aboveRun] });
    expect(aboveRun.result).toBe('success');
  });

  it('uses the raw run score for the per-run message while the stage is still running cleanup', () => {
    const runMessage = 'Canary score is not above the marginal score threshold.';
    const build = (canaryScore: number): any => ({
      stages: [
        {
          id: 'k1',
          type: KAYENTA_CANARY,
          status: 'RUNNING',
          name: 'canary',
          tasks: [],
          context: { intervalStageId: 'int', canaryConfig: { scoreThresholds: thresholds } },
        },
        {
          id: 'r0',
          type: RUN_CANARY,
          status: 'TERMINAL',
          parentStageId: 'int',
          tasks: [],
          context: { canaryScore, scoreThresholds: thresholds, canaryScoreMessage: runMessage },
        },
      ],
    });
    const failing = build(74.996);
    transformer.transform(null, failing);
    expect(kayentaOf(failing).exceptions).toEqual([runMessage]);

    const passing = build(75.004);
    transformer.transform(null, passing);
    expect(kayentaOf(passing).exceptions).toEqual([]);
  });

  describe('marginal-only thresholds, where rounding can wrongly fail a passing score', () => {
    const run = (score: number): any => {
      const execution = buildKayentaExecution([score]);
      kayentaOf(execution).context.canaryConfig.scoreThresholds = { marginal: 50, pass: null };
      transformer.transform(null, execution);
      return kayentaOf(execution);
    };

    it('keeps a raw 50.004 as a pass although it displays as 50', () => {
      const stage = run(50.004);
      expect(stage.context.overallScore).toBe(50);
      expect(stage.exceptions).toEqual([]);
      expect(stage.context.overallResult).toBe('success');
      expect(stage.context.overallHealth).toBeUndefined();
    });

    it('fails a raw 49.996 and a raw 50', () => {
      [49.996, 50].forEach((score) => {
        const stage = run(score);
        expect(stage.context.overallScore).toBe(50);
        expect(stage.exceptions).toEqual([message]);
        expect(stage.context.overallHealth).toBe('unhealthy');
      });
    });
  });

  it('uses the raw score with string scores and string thresholds', () => {
    const execution = buildKayentaExecution([74.996]);
    kayentaOf(execution).context.canaryConfig.scoreThresholds = { marginal: '50', pass: '75' };
    transformer.transform(null, execution);
    expect(kayentaOf(execution).exceptions).toEqual([message]);
    expect(kayentaOf(execution).context.overallHealth).toBe('unhealthy');
  });

  it('does not treat a missing or NaN score as failed or passed', () => {
    [[], [NaN], [undefined]].forEach((canaryScores) => {
      const execution = buildKayentaExecution(canaryScores);
      transformer.transform(null, execution);
      expect(kayentaOf(execution).exceptions).toEqual([]);
    });
  });
});
