import { ArtifactReferenceService } from './ArtifactReferenceService';
import { Registry } from 'core/registry';

const stage = (mixin: any) => ({
  name: 'name',
  type: 'foobar',
  refId: 'x',
  requisiteStageRefIds: [],
  ...mixin,
});

const registerTestStage = (fields: string[]) =>
  Registry.pipeline.registerStage({
    key: 'testStage',
    artifactRemover: ArtifactReferenceService.removeArtifactFromFields(fields),
  });

describe('ArtifactReferenceService', () => {
  beforeEach(() => Registry.reinitialize());

  describe('removeReferenceFromStages', () => {
    it('deletes reference from stage', () => {
      registerTestStage(['foo']);
      const stages = [stage({ type: 'testStage', foo: 'bar' })];
      ArtifactReferenceService.removeReferenceFromStages('bar', stages);
      expect(stages[0].foo).toBe(null);
    });

    it('deletes multiple references from a stage if registered to do so', () => {
      registerTestStage(['deployedManifest', 'requiredArtifactIds']);
      const stages = [stage({ type: 'testStage', deployedManifest: 'foo', requiredArtifactIds: 'foo' })];
      ArtifactReferenceService.removeReferenceFromStages('foo', stages);
      expect(stages[0].deployedManifest).toBe(null);
      expect(stages[0].requiredArtifactIds).toBe(null);
    });

    it('handles nested references', () => {
      registerTestStage(['foo.bar']);
      const stages = [stage({ type: 'testStage', foo: { bar: 'baz' } })];
      ArtifactReferenceService.removeReferenceFromStages('baz', stages);
      expect(stages[0].foo.bar).toBe(null);
    });

    it('doesnt delete reference from stage if reference doesnt match', () => {
      registerTestStage(['foo']);
      const stages = [stage({ type: 'testStage', foo: 'ref1' }), stage({ type: 'testStage', foo: 'ref2' })];
      ArtifactReferenceService.removeReferenceFromStages('ref1', stages);
      expect(stages[0].foo).toBe(null);
      expect(stages[1].foo).toBe('ref2');
    });

    it('doesnt delete reference if reference doesnt exist', () => {
      registerTestStage(['foo']);
      const stages = [stage({ type: 'testStage', bar: 'ref1' })];
      ArtifactReferenceService.removeReferenceFromStages('ref1', stages);
      expect(stages[0].bar).toBe('ref1');
    });

    it('splices nested reference from array', () => {
      registerTestStage(['foo']);
      const stages = [stage({ type: 'testStage', foo: ['ref1', 'ref2', 'ref3'] })];
      ArtifactReferenceService.removeReferenceFromStages('ref2', stages);
      expect(stages[0].foo.length).toBe(2);
      expect(stages[0].foo[0]).toBe('ref1');
      expect(stages[0].foo[1]).toBe('ref3');
    });

    it('doesnt splice nested reference from array if reference doesnt match', () => {
      registerTestStage(['foo']);
      const stages = [stage({ type: 'testStage', foo: ['ref1', 'ref2', 'ref3'] })];
      ArtifactReferenceService.removeReferenceFromStages('not found reference', stages);
      expect(stages[0].foo.length).toBe(3);
      expect(stages[0].foo[0]).toBe('ref1');
      expect(stages[0].foo[1]).toBe('ref2');
      expect(stages[0].foo[2]).toBe('ref3');
    });

    it('calls a custom artifact remover if defined', () => {
      const remover = jasmine.createSpy('remover').and.stub();
      Registry.pipeline.registerStage({
        key: 'testStage',
        artifactRemover: remover,
      });
      const stages = [stage({ type: 'testStage' })];
      ArtifactReferenceService.removeReferenceFromStages('ref', stages);
      expect(remover).toHaveBeenCalledWith(stages[0], 'ref');
    });

    it('does not call a custom artifact remover if stage type is incorrect', () => {
      const remover = jasmine.createSpy('remover').and.stub();
      Registry.pipeline.registerStage({
        key: 'testStage',
        artifactRemover: remover,
      });
      const stages = [stage({ type: 'noMatchStage' })];
      ArtifactReferenceService.removeReferenceFromStages('ref', stages);
      expect(remover).toHaveBeenCalledTimes(0);
    });
  });
});
