import type { IStage } from '../../../../domain';
import { validateUploadArtifactToObjectStorageStage } from './uploadArtifactToObjectStorageValidator';

describe('uploadArtifactToObjectStorageValidator', () => {
  const createStage = (overrides: Partial<IStage> = {}): IStage => ({
    name: 'Upload Artifact',
    type: 'uploadArtifactToObjectStorage',
    refId: '1',
    requisiteStageRefIds: [],
    cloudProvider: 'aws',
    credentials: 'my-account',
    region: 'us-east-1',
    bucket: 'my-bucket',
    objectKeyPrefix: 'releases/latest',
    ...overrides,
  });

  it('should return no errors when all required fields are provided', () => {
    const stage = createStage();
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(Object.keys(errors).length).toBe(0);
  });

  it('should return error when cloudProvider is missing', () => {
    const stage = createStage({ cloudProvider: undefined });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(errors.cloudProvider).toBeDefined();
  });

  it('should return error when credentials is missing', () => {
    const stage = createStage({ credentials: undefined });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(errors.credentials).toBeDefined();
  });

  it('should return error when region is missing', () => {
    const stage = createStage({ region: undefined });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(errors.region).toBeDefined();
  });

  it('should return error when bucket is missing', () => {
    const stage = createStage({ bucket: undefined });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(errors.bucket).toBeDefined();
  });

  it('should return error when objectKeyPrefix is missing', () => {
    const stage = createStage({ objectKeyPrefix: undefined });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(errors.objectKeyPrefix).toBeDefined();
  });

  it('should return multiple errors when multiple fields are missing', () => {
    const stage = createStage({
      cloudProvider: undefined,
      credentials: undefined,
      region: undefined,
      bucket: undefined,
      objectKeyPrefix: undefined,
    });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(Object.keys(errors).length).toBe(5);
  });

  it('should allow SpEL expressions in bucket field', () => {
    const stage = createStage({ bucket: '${parameters.bucket}' });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(errors.bucket).toBeUndefined();
  });

  it('should allow SpEL expressions in objectKeyPrefix field', () => {
    const stage = createStage({ objectKeyPrefix: '${trigger.buildNumber}' });
    const errors = validateUploadArtifactToObjectStorageStage(stage, {});
    expect(errors.objectKeyPrefix).toBeUndefined();
  });
});
