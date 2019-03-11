import * as React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { mock } from 'angular';
import { REACT_MODULE } from 'core/reactShims';

import { IArtifact, IExpectedArtifact } from 'core/domain';
import { Artifact } from 'core/pipeline/status/Artifact';

import { ResolvedArtifactList, IResolvedArtifactListProps } from './ResolvedArtifactList';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';

describe('<ResolvedArtifactList/>', () => {
  let component: ShallowWrapper<IResolvedArtifactListProps>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  it('renders null when null artifacts are passed in', function() {
    const artifacts: IArtifact[] = null;
    component = shallow(<ResolvedArtifactList artifacts={artifacts} />);
    expect(component.get(0)).toEqual(null);
  });

  it('renders null when 0 artifacts are passed in', function() {
    const artifacts: IArtifact[] = [];
    const resolvedExpectedArtifacts = artifacts.map(a => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList artifacts={artifacts} resolvedExpectedArtifacts={resolvedExpectedArtifacts} />,
    );
    expect(component.get(0)).toEqual(null);
  });

  it('renders a list when artifacts are passed in', function() {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    const resolvedExpectedArtifacts = artifacts.map(a => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList artifacts={artifacts} resolvedExpectedArtifacts={resolvedExpectedArtifacts} />,
    );
    expect(component.find(Artifact).length).toEqual(1);
  });

  it('does not render an artifact without a type and name', function() {
    const singleArtifact: IArtifact[] = [
      {
        id: 'abcd',
      },
    ];
    const resolvedExpectedArtifacts = singleArtifact.map(a => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList artifacts={singleArtifact} resolvedExpectedArtifacts={resolvedExpectedArtifacts} />,
    );
    expect(component.get(0)).toEqual(null);
  });

  it('renders an artifacts that does have a type and name', function() {
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
    const resolvedExpectedArtifacts = artifacts.map(a => ({ boundArtifact: a } as IExpectedArtifact));
    component = shallow(
      <ResolvedArtifactList artifacts={artifacts} resolvedExpectedArtifacts={resolvedExpectedArtifacts} />,
    );
    expect(component.find(Artifact).length).toEqual(1);
  });

  it('does not render artifacts for which there is no expected artifact in the pipeline', function() {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    component = shallow(<ResolvedArtifactList artifacts={artifacts} />);
    const li = component.find('li');
    expect(li.text()).toMatch(/1.*artifact.*not.*consumed/);
  });
});
