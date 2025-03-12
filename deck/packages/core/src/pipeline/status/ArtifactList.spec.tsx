import { mock } from 'angular';
import type { ShallowWrapper } from 'enzyme';
import { shallow } from 'enzyme';
import React from 'react';

import { Artifact } from './Artifact';
import type { IArtifactListProps } from './ArtifactList';
import { ArtifactList } from './ArtifactList';
import type { IArtifact } from '../../domain';
import { REACT_MODULE } from '../../reactShims';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';

describe('<ArtifactList/>', () => {
  let component: ShallowWrapper<IArtifactListProps>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  it('renders null when null artifacts are passed in', function () {
    const artifacts: IArtifact[] = null;
    component = shallow(<ArtifactList artifacts={artifacts} />);
    expect(component.get(0)).toEqual(null);
  });

  it('renders null when 0 artifacts are passed in', function () {
    const artifacts: IArtifact[] = [];
    component = shallow(<ArtifactList artifacts={artifacts} />);
    expect(component.get(0)).toEqual(null);
  });

  it('renders a list when artifacts are passed in', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
      {
        id: 'defg',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    component = shallow(<ArtifactList artifacts={artifacts} />);
    expect(component.find(Artifact).length).toEqual(2);
  });
});
