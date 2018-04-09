import { ArtifactReferenceServiceProvider } from './ArtifactReferenceService';

const stage = (mixin: any) =>
  Object.assign(
    {},
    {
      name: 'name',
      type: 'foobar',
      refId: 'x',
      requisiteStageRefIds: [],
    },
    mixin,
  );

describe('ArtifactReferenceService', () => {
  let svc: ArtifactReferenceServiceProvider;

  beforeEach(() => {
    svc = new ArtifactReferenceServiceProvider();
  });

  describe('removeReferenceFromStages', () => {
    it('deletes reference from stage', () => {
      const stages = [stage({ foo: 'bar' })];
      const refs = () => [['foo']];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('bar', stages);
      expect(stages[0].foo).toBe(undefined);
    });

    it('deletes multiple references from a stage if registered to do so', () => {
      const stages = [stage({ deployedManifest: 'foo', requiredArtifactIds: 'foo' })];
      const refs = () => [['deployedManifest'], ['requiredArtifactIds']];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('foo', stages);
      expect(stages[0].deployedManifest).toBe(undefined);
      expect(stages[0].requiredArtifactIds).toBe(undefined);
    });

    it('doesnt delete reference from stage if reference doesnt match', () => {
      const stages = [stage({ foo: 'ref1' }), stage({ foo: 'ref2' })];
      const refs = () => [['foo']];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('ref1', stages);
      expect(stages[0].foo).toBe(undefined);
      expect(stages[1].foo).toBe('ref2');
    });

    it('doesnt delete reference if reference doesnt exist', () => {
      const stages = [stage({ foo: 'ref1' })];
      const refs = () => [['foo', 'bar']];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('ref1', stages);
      expect(stages[0].foo).toBe('ref1');
    });

    it('deletes nested references', () => {
      const stages = [stage({ foo: [{ baz: 'ref1' }] })];
      const refs = () => [['foo', 0, 'baz']];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('ref1', stages);
      expect(stages[0].foo[0].baz).toBe(undefined);
    });

    it('doesnt delete nested references if reference doesnt match', () => {
      const stages = [stage({ foo: [{ baz: 'ref1' }] }), stage({ foo: [{ baz: 'ref2' }] })];
      const refs = () => [['foo', 0, 'baz']];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('ref1', stages);
      expect(stages[0].foo[0].baz).toBe(undefined);
      expect(stages[1].foo[0].baz).toBe('ref2');
    });

    it('splices nested reference from array', () => {
      const stages = [stage({ path: { to: { reference: ['ref1', 'ref2', 'ref3'] } } })];
      const refs = () => [['path', 'to', 'reference', 1]];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('ref2', stages);
      expect(stages[0].path.to.reference.length).toBe(2);
      expect(stages[0].path.to.reference[0]).toBe('ref1');
      expect(stages[0].path.to.reference[1]).toBe('ref3');
    });

    it('doesnt splice nested reference from array if reference doesnt match', () => {
      const stages = [stage({ path: { to: { reference: ['ref1', 'ref2', 'ref3'] } } })];
      const refs = () => [['path', 'to', 'reference', 1]];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('not found reference', stages);
      expect(stages[0].path.to.reference.length).toBe(3);
      expect(stages[0].path.to.reference[0]).toBe('ref1');
      expect(stages[0].path.to.reference[1]).toBe('ref2');
      expect(stages[0].path.to.reference[2]).toBe('ref3');
    });

    it('splices nested reference from array when only the path to the array is given', () => {
      const stages = [stage({ path: { to: { reference: ['ref1', 'ref2', 'ref3'] } } })];
      const refs = () => [['path', 'to', 'reference']];
      svc.registerReference('stage', refs);
      svc.removeReferenceFromStages('ref2', stages);
      expect(stages[0].path.to.reference.length).toBe(2);
      expect(stages[0].path.to.reference[0]).toBe('ref1');
      expect(stages[0].path.to.reference[1]).toBe('ref3');
    });
  });
});
