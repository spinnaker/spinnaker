import { hri as HumanReadableIds } from 'human-readable-ids';

import type { IPipeline, IPipelineTemplateV2 } from '../../../../domain';
import { PipelineTemplateV2Service } from './pipelineTemplateV2.service';
import { UUIDGenerator } from '../../../../utils';

describe('PipelineTemplateV2Service', () => {
  describe('createPipelineTemplate()', () => {
    const mockId = 'd952f77c-b043-4e3d-950c-873992dcd689';
    const mockName = 'my-template-1';
    const mockOwner = 'example@example.com';
    const mockPipeline: Partial<IPipeline> = {
      keepWaitingPipelines: false,
      lastModifiedBy: 'anonymous',
      limitConcurrent: true,
      stages: [{ name: 'Find Image from Cluster', refId: '1', requisiteStageRefIds: [], type: 'findImage' }],
    };

    const mockTemplate: IPipelineTemplateV2 = {
      id: mockId,
      metadata: {
        description: `A pipeline template derived from pipeline "${mockPipeline.name}" in application "${mockPipeline.application}"`,
        name: mockName,
        owner: mockOwner,
        scopes: ['global'],
      },
      pipeline: mockPipeline as IPipeline,
      protect: false,
      schema: 'v2',
      variables: [],
    };

    // Set in beforeEach (not beforeAll): the suite runs with restoreMocks, which restores spies
    // before each test, so beforeAll-registered spies would not survive to the test body.
    beforeEach(() => {
      vi.spyOn(UUIDGenerator, 'generateUuid').mockReturnValue(mockId);
      vi.spyOn(HumanReadableIds, 'random').mockReturnValue(mockName);
    });

    it('returns a template successfully', () => {
      const template = PipelineTemplateV2Service.createPipelineTemplate(mockPipeline as IPipeline, mockOwner);
      expect(template).toEqual(mockTemplate);
    });
  });
});
