import { trim } from 'lodash';

import { IManifest, IManifestEvent } from '../domain';

// when fetching logs from Pods there are some instances
// where we know the name of the Pod ahead of time and some
// where we must extract the name of the Pod from a Kubernetes
// event. IPodNameProvider allows us to inject a Pod name into
// components which need them based on a given situation where
// the caller knows how to the name should be supplied.
export interface IPodNameProvider {
  getPodName(): string;
}

export class DefaultPodNameProvider implements IPodNameProvider {
  private podName: string;

  constructor(podName: string) {
    this.podName = podName;
  }

  public getPodName(): string {
    return this.podName;
  }
}

export class JobEventBasedPodNameProvider implements IPodNameProvider {
  private manifest: IManifest;
  private manifestEvent: IManifestEvent;

  constructor(manifest: IManifest, manifestEvent: IManifestEvent) {
    this.manifest = manifest;
    this.manifestEvent = manifestEvent;
  }

  public getPodName(): string {
    const { manifestEvent } = this;
    return this.canParsePodName() ? trim(manifestEvent.message?.split(':')[1]) : '';
  }

  private canParsePodName(): boolean {
    const { manifest, manifestEvent } = this;
    return (
      !!manifest.manifest &&
      !!manifest.manifest.status &&
      !!manifestEvent &&
      !!manifestEvent.message?.startsWith('Created pod') &&
      manifest.manifest.kind.toLowerCase() === 'job'
    );
  }
}
