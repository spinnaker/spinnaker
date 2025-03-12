import type {
  ICreationMetadata,
  ICreationMetadataTag,
  IEntityRef,
  IEntityTag,
  IEntityTags,
  IEntityTagsMetadata,
} from '@spinnaker/core';

export const mockEntityTagsMetadata: IEntityTagsMetadata = {
  created: 1580755347841,
  createdBy: 'user@test.com',
  lastModified: 1580755347842,
  lastModifiedBy: 'user@test.com',
  name: 'test:metadata',
};

export const mockCreationMetadata: ICreationMetadata = {
  executionType: 'orchestration',
  stageId: '12345',
  executionId: '123',
  pipelineConfigId: '456',
  application: 'testapp',
  user: 'user@test.com',
  description: 'Server group created',
};

export const createMockEntityTag = (category?: string, value?: any): IEntityTag => ({
  ...mockEntityTagsMetadata,
  category: category || 'deprecation',
  value: value || {
    type: 'alert',
    message: 'This is an alert',
  },
});

export const createMockCreationMetadataTag = (value?: ICreationMetadata): ICreationMetadataTag => ({
  ...mockEntityTagsMetadata,
  value: value || mockCreationMetadata,
});

export const createMockEntityRef = (entityType?: string, region?: string): IEntityRef => ({
  account: 'test',
  accountId: '123123',
  application: 'testapp',
  cloudProvider: 'aws',
  entityId: 'testapp-v000',
  entityType: entityType || 'servergroup',
  region: region || 'us-east-1',
});

export const mockEntityTags: IEntityTags = {
  id: '123',
  idPattern: '{{id}}',
  lastModified: 1580755347842,
  lastModifiedBy: 'user@test.com',
  tags: [createMockEntityTag(), createMockEntityTag('alert')],
  tagsMetadata: [mockEntityTagsMetadata, mockEntityTagsMetadata],
  entityRef: createMockEntityRef(),
  alerts: [createMockEntityTag()],
  notices: [],
  creationMetadata: createMockCreationMetadataTag(),
};
