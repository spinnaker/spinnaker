import React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { mock } from 'angular';
import { REACT_MODULE } from '../../reactShims';

import { IArtifact, IExpectedArtifact } from '../../domain';
import { Artifact } from './Artifact';

import { ResolvedArtifactList, IResolvedArtifactListProps } from './ResolvedArtifactList';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';

describe('<ResolvedArtifactList/>', () => {
  let component: ShallowWrapper<IResolvedArtifactListProps>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  it('renders null when null artifacts are passed in', function () {
    const artifacts: IArtifact[] = null;
    component = shallow(<ResolvedArtifactList artifacts={artifacts} showingExpandedArtifacts={true} />);
    expect(component.get(0)).toEqual(null);
  });

  it('renders null when 0 artifacts are passed in', function () {
    const artifacts: IArtifact[] = [];
    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );
    expect(component.get(0)).toEqual(null);
  });

  it('renders null when artifacts are set to not expanded', () => {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={false}
      />,
    );
    expect(component.get(0)).toEqual(null);
  });

  it('renders two columns when columnLayoutAfter is set to 2', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
      {
        id: 'efgh',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];

    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );

    expect(component.find('.artifact-list-column').length).toEqual(2);
    expect(component.find(Artifact).length).toEqual(2);
  });

  it('does not render an artifact without a type and name', function () {
    const singleArtifact: IArtifact[] = [
      {
        id: 'abcd',
      },
    ];
    const resolvedExpectedArtifacts = singleArtifact.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList
        artifacts={singleArtifact}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );
    expect(component.get(0)).toEqual(null);
  });

  it('only renders an artifacts that has a type and name', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
      },
      {
        id: 'abcd2',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );
    expect(component.find(Artifact).length).toEqual(1);
  });

  it('does not render artifacts for which there is no expected artifact in the pipeline', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    component = shallow(<ResolvedArtifactList artifacts={artifacts} showingExpandedArtifacts={true} />);
    const li = component.find('.extraneous-artifacts');
    expect(li.text()).toMatch(/1.*artifact.*not.*consumed/);
  });
});
