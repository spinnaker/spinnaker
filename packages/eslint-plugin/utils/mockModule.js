function mockModule(moduleName) {
  jest.mock(moduleName);
  const mockedModule = require(moduleName);
  const actualModule = jest.requireActual(moduleName);
  for ([key, value] of Object.entries(actualModule)) {
    if (typeof value === 'function') {
      mockedModule[key].mockImplementation(value);
    }
  }

  return require(moduleName);
}

module.exports = mockModule;
