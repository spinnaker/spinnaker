import { RetryService } from '@spinnaker/core';

import { DockerChartImageReader, DockerImageReader } from './DockerImageReader';

describe('Docker image readers', () => {
  it('passes owner signals through all image and tag retry sequences', async () => {
    const retry = spyOn(RetryService, 'buildRetrySequence').and.returnValue(Promise.resolve([]));
    const signal = new AbortController().signal;
    const imageParams = { provider: 'dockerRegistry', account: 'registry.example' };
    const tagParams = { ...imageParams, repository: 'example/service' };

    await Promise.all([
      DockerImageReader.findImages(imageParams, signal),
      DockerImageReader.findTags(tagParams, signal),
      DockerChartImageReader.findImages(imageParams, signal),
      DockerChartImageReader.findTags(tagParams, signal),
    ]);

    expect(retry.calls.allArgs().map((args) => args[4])).toEqual([signal, signal, signal, signal]);
  });
});
