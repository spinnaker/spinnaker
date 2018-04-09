import * as React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { mock } from 'angular';
import { REACT_MODULE } from 'core/reactShims';
import { IArtifact } from 'core/domain';
import { ArtifactList, IArtifactListProps, IArtifactListState } from './ArtifactList';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';

describe('<ArtifactList/>', () => {
  let component: ShallowWrapper<IArtifactListProps, IArtifactListState>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  it('renders null when null artifacts are passed in', function() {
    const artifacts: IArtifact[] = [];
    component = shallow(<ArtifactList artifacts={artifacts} />);
    expect(component.get(0)).toEqual(null);
  });

  it('renders null when 0 artifacts are passed in', function() {
    const artifacts: IArtifact[] = [];
    component = shallow(<ArtifactList artifacts={artifacts} />);
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
    component = shallow(<ArtifactList artifacts={artifacts} />);
    expect(component.find('ul.trigger-details.artifacts').length).toEqual(1);
  });

  it("renders an artifact's type and name", function() {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    component = shallow(<ArtifactList artifacts={artifacts} />);
    const li = component.find('li');
    const dt = li.find('dt');
    const dd = li.find('dd');
    expect(li.length).toEqual(1);
    expect(dt.length).toEqual(2);
    expect(dd.length).toEqual(2);
    expect(dt.at(0).text()).toEqual('Type');
    expect(dd.at(0).text()).toEqual(ARTIFACT_TYPE);
    expect(dt.at(1).text()).toEqual('Artifact');
    expect(dd.at(1).text()).toEqual(ARTIFACT_NAME);
  });

  it('renders an artifact version if present', function() {
    const version = 'v001';
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
        version: version,
      },
    ];
    component = shallow(<ArtifactList artifacts={artifacts} />);
    const li = component.find('li');
    expect(li.find('dd').length).toEqual(3);
    expect(
      li
        .find('dd')
        .at(2)
        .text(),
    ).toEqual(version);
  });
});
