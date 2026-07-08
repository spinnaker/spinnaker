describe('Docker package entrypoint', () => {
  it('does not expose an Angular module token', () => {
    const dockerPackage = require('./index');

    expect((dockerPackage as any).DOCKER_MODULE).toBeUndefined();
  });
});
