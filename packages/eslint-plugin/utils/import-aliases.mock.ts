const mock = mockModule('./import-aliases');
mock.getAllSpinnakerPackages.mockImplementation(() => ['core', 'amazon', 'kubernetes']);
export default mock;

export function mockModule(moduleName: string) {
  jest.mock(moduleName);
  const mockedModule = require(moduleName);
  const actualModule = jest.requireActual(moduleName);
  for (const tuple of Object.entries(actualModule)) {
    const [key, value] = tuple;
    if (typeof value === 'function') {
      mockedModule[key].mockImplementation(value);
    }
  }

  return require(moduleName);
}
