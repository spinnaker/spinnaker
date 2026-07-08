describe('Tencentcloud package entrypoint', () => {
  it('loads successfully', () => {
    expect(() => require('./index')).not.toThrow();
  });

  it('does not expose an Angular module token', () => {
    const tencentcloudPackage = require('./index');

    expect((tencentcloudPackage as any).TENCENTCLOUD_MODULE).toBeUndefined();
  });
});
