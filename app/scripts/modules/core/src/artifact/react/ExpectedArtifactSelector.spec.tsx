import React from 'react';
import { mount } from 'enzyme';

import { ExpectedArtifactService } from '..';
import { IExpectedArtifact } from '../../domain';

import { ExpectedArtifactSelector } from './ExpectedArtifactSelector';

const artifact = (type: string): IExpectedArtifact => {
  const ea = ExpectedArtifactService.createEmptyArtifact();
  ea.matchArtifact.customKind = false;
  ea.matchArtifact.type = type;
  return ea;
};

describe('<ExpectedArtifactSelector/>', () => {
  describe('filtering offered artifact types', () => {
    it('only includes those artifacts with type matching a single offeredArtifactTypes regex', () => {
      const artifacts = [artifact('gcs/object'), artifact('docker/image')];
      const sel = mount(
        <ExpectedArtifactSelector
          expectedArtifacts={artifacts}
          onChange={(_ea) => {}}
          offeredArtifactTypes={[/.*gcs.*/]}
        />,
      );
      const filteredArtifacts = sel.find('TetheredSelect').prop('options') as any[];
      expect(filteredArtifacts.length).toBe(1);
      expect(filteredArtifacts[0].expectedArtifact.matchArtifact.type).toBe('gcs/object');
    });

    it('only includes those artifacts whose type matches any of the regexes in the offeredArtifactTypes array', () => {
      const artifacts = [artifact('gcs/object'), artifact('foo/bar'), artifact('docker/image')];
      const sel = mount(
        <ExpectedArtifactSelector
          expectedArtifacts={artifacts}
          onChange={(_ea) => {}}
          offeredArtifactTypes={[/.*gcs.*/, /.*docker.*/]}
        />,
      );
      const filteredArtifacts = sel.find('TetheredSelect').prop('options') as any[];
      expect(filteredArtifacts.length).toBe(2);
      expect(filteredArtifacts[0].expectedArtifact.matchArtifact.type).toBe('gcs/object');
      expect(filteredArtifacts[1].expectedArtifact.matchArtifact.type).toBe('docker/image');
    });
  });

  describe('excluding artifact types', () => {
    it('excludes single artifact types defined by regex', () => {
      const artifacts = [artifact('gcs/object'), artifact('docker/image')];
      const sel = mount(
        <ExpectedArtifactSelector
          expectedArtifacts={artifacts}
          onChange={(_ea) => {}}
          excludedArtifactTypes={[/.*gcs.*/]}
        />,
      );
      const filteredArtifacts = sel.find('TetheredSelect').prop('options') as any[];
      expect(filteredArtifacts.length).toBe(1);
      expect(filteredArtifacts[0].expectedArtifact.matchArtifact.type).toBe('docker/image');
    });

    it('excludes multiple artifact types defined by regex', () => {
      const artifacts = [artifact('gcs/object'), artifact('gcs/bucket'), artifact('docker/image')];
      const sel = mount(
        <ExpectedArtifactSelector
          expectedArtifacts={artifacts}
          onChange={(_ea) => {}}
          excludedArtifactTypes={[/.*gcs.*/]}
        />,
      );
      const filteredArtifacts = sel.find('TetheredSelect').prop('options') as any[];
      expect(filteredArtifacts.length).toBe(1);
      expect(filteredArtifacts[0].expectedArtifact.matchArtifact.type).toBe('docker/image');
    });

    it('excludes multiple artifact types defined by multiples regexes', () => {
      const artifacts = [artifact('gcs/object'), artifact('docker/image')];
      const sel = mount(
        <ExpectedArtifactSelector
          expectedArtifacts={artifacts}
          onChange={(_ea) => {}}
          excludedArtifactTypes={[/.*gcs.*/, /.*docker.*/]}
        />,
      );
      const filteredArtifacts = sel.find('TetheredSelect').prop('options') as any[];
      expect(filteredArtifacts.length).toBe(0);
    });
  });

  describe('creating a new artifact', () => {
    it('provides an option to create a new artifact when an onRequestCreate prop is given', () => {
      const artifacts = [artifact('gcs/object')];
      const sel = mount(
        <ExpectedArtifactSelector expectedArtifacts={artifacts} onChange={(_ea) => {}} onRequestCreate={() => {}} />,
      );
      const options = sel.find('TetheredSelect').prop('options') as any[];
      expect(options.length).toBe(2);
    });

    it('doesnt provide an option to create a new artifact when an onRequestCreate prop is not given', () => {
      const artifacts = [artifact('gcs/object')];
      const sel = mount(<ExpectedArtifactSelector expectedArtifacts={artifacts} onChange={(_ea) => {}} />);
      const options = sel.find('TetheredSelect').prop('options') as any[];
      expect(options.length).toBe(1);
    });
  });
});
