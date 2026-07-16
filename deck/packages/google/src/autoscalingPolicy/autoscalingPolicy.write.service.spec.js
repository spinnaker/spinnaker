'use strict';

import { TaskExecutor } from '@spinnaker/core';

describe('gceAutoscalingPolicyWriter', () => {
  let writer;

  beforeEach(
    window.module(
      require('./autoscalingPolicy.write.service').GOOGLE_AUTOSCALINGPOLICY_AUTOSCALINGPOLICY_WRITE_SERVICE,
    ),
  );

  beforeEach(
    window.inject(function (gceAutoscalingPolicyWriter) {
      writer = gceAutoscalingPolicyWriter;
      spyOn(TaskExecutor, 'executeTask').and.returnValue('task');
    }),
  );

  it('strips legacy maxUnavailable before submitting autohealing upserts', function () {
    writer.upsertAutoHealingPolicy(
      { name: 'myapp' },
      { type: 'gce', account: 'test-account', region: 'us-central1', name: 'myapp-main-v000' },
      {
        healthCheck: 'hc',
        initialDelaySec: 30,
        maxUnavailable: { fixed: 3 },
      },
    );

    const job = TaskExecutor.executeTask.calls.mostRecent().args[0].job[0];
    expect(job.autoHealingPolicy).toEqual({
      healthCheck: 'hc',
      initialDelaySec: 30,
    });
    expect(job.autoHealingPolicy.maxUnavailable).toBeUndefined();
  });
});
