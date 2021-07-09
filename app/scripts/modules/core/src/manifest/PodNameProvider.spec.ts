import { DefaultPodNameProvider, JobEventBasedPodNameProvider } from './PodNameProvider';
import { IManifest, IManifestEvent } from '../domain';

describe('PodNameProvider', function () {
  describe('DefaultPodNameProvider', function () {
    it('returns the pod name supplied to it', function () {
      const podName = 'test';
      const provider = new DefaultPodNameProvider(podName);
      expect(provider.getPodName()).toBe(podName);
    });
  });

  describe('JobEventBasedPodNameProvider', function () {
    it('returns a pod name parsed from a message', function () {
      const podName = 'test';
      const manifest = { manifest: { kind: 'Job', status: true } } as IManifest;
      const manifestEvent = { message: `Created pod: ${podName}` } as IManifestEvent;
      const provider = new JobEventBasedPodNameProvider(manifest, manifestEvent);
      expect(provider.getPodName()).toBe(podName);
    });

    it('returns a empty string if manifest is not of type Job', function () {
      const podName = 'test';
      const manifest = { manifest: { kind: 'Deployment', status: true } } as IManifest;
      const manifestEvent = { message: `Created pod: ${podName}` } as IManifestEvent;
      const provider = new JobEventBasedPodNameProvider(manifest, manifestEvent);
      expect(provider.getPodName()).toBe('');
    });

    it('returns empty string for messages that do not start with Created pod', function () {
      const podName = 'test';
      const manifest = { manifest: { kind: 'Job', status: true } } as IManifest;
      const manifestEvent = { message: `Killed pod: ${podName}` } as IManifestEvent;
      const provider = new JobEventBasedPodNameProvider(manifest, manifestEvent);
      expect(provider.getPodName()).toBe('');
    });
  });
});
