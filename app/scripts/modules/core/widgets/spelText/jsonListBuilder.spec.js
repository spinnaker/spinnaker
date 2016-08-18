'use strict';

describe('jsonListBuilder', function () {

  let builder;

  beforeEach(
    window.module(
      require('./jsonListBuilder')
    )
  );

  beforeEach(
    window.inject(function(jsonListBuilder) {
      builder = jsonListBuilder;
    })
  );

  describe('create a list with only the leaf keys', function () {
    it('a simple json with one attribute', function () {
      let json = { name: 'foo'};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name']`]);
    });


    it('a simple json with multiple attributes', function () {
      let json = { name: 'foo', bar: 'baz'};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name']`, `['bar']`]);
    });

    it('nested objects', function () {
      let json = { name: {foo: 'bar'}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name']['foo']`]);
    });

    it('nested objects with multivalues', function () {
      let json = { name: {foo: 'bar', baz: 2}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name']['foo']`, `['name']['baz']`]);
    });

    it('deep nested objects', function () {
      let json = { name: { baz: { context: {foo: 2}}}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name']['baz']['context']['foo']`]);
    });

    it('deep nested objects with multivalues', function () {
      let json = { name: { baz: { context: {foo: 2, boom: 'go'}}}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([ `['name']['baz']['context']['foo']`, `['name']['baz']['context']['boom']`]);
    });

    it('json with an array of strings', function () {
      let json = { name: ['foo'] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name'][0]`]);
    });

    it('json with an array of multiple strings', function () {
      let json = { name: ['foo', 'bar'] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name'][0]`, `['name'][1]`]);
    });

    it('json with an array of one object', function () {
      let json = { name: [{foo: 2}] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name'][0]['foo']`]);
    });

    it('json with an array of two object', function () {
      let json = { name: [{foo: 2}, {foo:3}] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['name'][1]['foo']`, `['name'][0]['foo']`]);
    });

    it('json with dots in the key name', function () {
      let json = { 'notification.type': 'foo', 'deploy.server.group': {'server.group.name': 'baz'} };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([`['notification.type']`,`['deploy.server.group']['server.group.name']`]);
    });

  });

  describe('create list with and exclude list', function () {
    it('should exclude the top task node from the list', function () {
      let json = { name: {foo: 2}, task:{bar: 3}};
      let ignoreList = ['task'];
      let result = builder.convertJsonKeysToBracketedList(json, ignoreList);
      expect(result).toEqual([`['name']['foo']`]);
    });

    it('should include the task node deeper in the tree from the list', function () {
      let json = { name: {foo: 2}, joy: {task:{bar: 3}}};
      let ignoreList = ['name'];
      let result = builder.convertJsonKeysToBracketedList(json, ignoreList);
      expect(result).toEqual([`['joy']['task']['bar']`]);
    });
  });

  describe('escape string for regex', function () {
    it('should escape parens', function () {
      let item = 'Deploy (safari, release, 10%)';
      let result = builder.escapeForRegEx(item);
      expect(result).toEqual('Deploy \\(safari, release, 10%\\)');
    });
  });

});
