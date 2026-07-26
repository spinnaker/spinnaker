import { PipelineTemplates } from './index';

describe('PipelineTemplates', () => {
  it('does not expose the removed bake extended attributes Angular template', () => {
    expect((PipelineTemplates as any).addExtendedAttributes).toBeUndefined();
  });
});
