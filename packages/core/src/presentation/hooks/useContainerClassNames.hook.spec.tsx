import React from 'react';
import { mount } from 'enzyme';
import { useContainerClassNames } from './useContainerClassNames.hook';

const TestComponent = ({ classNames }: { classNames: string[] }) => {
  useContainerClassNames(classNames);
  return null as JSX.Element;
};

const containerClassName = 'spinnaker-container';
const testClassNames = ['testClass1', 'testClass2'];

describe('useContainerClassNames', () => {
  let containerElement: HTMLDivElement;

  beforeEach(() => {
    containerElement = document.createElement('div');
    containerElement.classList.add(containerClassName);
  });

  it('should add class names to the container element when mounted', () => {
    spyOn(document, 'querySelector').and.returnValue(containerElement);

    mount(<TestComponent classNames={testClassNames} />);

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(true);
  });

  it('should remove class names from the container element when unmounted', () => {
    spyOn(document, 'querySelector').and.returnValue(containerElement);

    const component = mount(<TestComponent classNames={testClassNames} />);

    component.render();
    component.unmount();

    expect(containerElement.classList.contains(testClassNames[0])).toBe(false);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(false);
  });

  it('should add and remove class names when the elements in "classNames" change', () => {
    spyOn(document, 'querySelector').and.returnValue(containerElement);

    const component = mount(<TestComponent classNames={testClassNames} />);

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(true);

    component.setProps({ classNames: testClassNames.concat('additionalClass') });
    component.render();

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(true);
    expect(containerElement.classList.contains('additionalClass')).toBe(true);

    component.setProps({ classNames: [testClassNames[0]] });
    component.render();

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(false);
    expect(containerElement.classList.contains('additionalClass')).toBe(false);

    component.setProps({ classNames: [] });
    component.render();

    expect(containerElement.classList.contains(testClassNames[0])).toBe(false);
  });

  it('should silently do nothing when the container element does not exist', () => {
    spyOn(document, 'querySelector').and.returnValue(null);

    expect(() => mount(<TestComponent classNames={testClassNames} />)).not.toThrow();
  });
});
