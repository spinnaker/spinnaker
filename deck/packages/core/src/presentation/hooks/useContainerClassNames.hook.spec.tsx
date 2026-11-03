import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useContainerClassNames } from './useContainerClassNames.hook';

const containerClassName = 'spinnaker-container';
const testClassNames = ['testClass1', 'testClass2'];

describe('useContainerClassNames', () => {
  let containerElement: HTMLDivElement;

  beforeEach(() => {
    containerElement = document.createElement('div');
    containerElement.classList.add(containerClassName);
  });

  it('should add class names to the container element when mounted', () => {
    vi.spyOn(document, 'querySelector').mockReturnValue(containerElement);

    renderHookHarness(({ classNames }) => useContainerClassNames(classNames), { classNames: testClassNames });

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(true);
  });

  it('should remove class names from the container element when unmounted', () => {
    vi.spyOn(document, 'querySelector').mockReturnValue(containerElement);

    const rendered = renderHookHarness(({ classNames }) => useContainerClassNames(classNames), {
      classNames: testClassNames,
    });

    rendered.unmount();

    expect(containerElement.classList.contains(testClassNames[0])).toBe(false);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(false);
  });

  it('should add and remove class names when the elements in "classNames" change', () => {
    vi.spyOn(document, 'querySelector').mockReturnValue(containerElement);

    const rendered = renderHookHarness(({ classNames }) => useContainerClassNames(classNames), {
      classNames: testClassNames,
    });

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(true);

    rendered.rerenderHook({ classNames: testClassNames.concat('additionalClass') });

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(true);
    expect(containerElement.classList.contains('additionalClass')).toBe(true);

    rendered.rerenderHook({ classNames: [testClassNames[0]] });

    expect(containerElement.classList.contains(testClassNames[0])).toBe(true);
    expect(containerElement.classList.contains(testClassNames[1])).toBe(false);
    expect(containerElement.classList.contains('additionalClass')).toBe(false);

    rendered.rerenderHook({ classNames: [] });

    expect(containerElement.classList.contains(testClassNames[0])).toBe(false);
  });

  it('should silently do nothing when the container element does not exist', () => {
    vi.spyOn(document, 'querySelector').mockReturnValue(null);

    expect(() =>
      renderHookHarness(({ classNames }) => useContainerClassNames(classNames), { classNames: testClassNames }),
    ).not.toThrow();
  });
});
