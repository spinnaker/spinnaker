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
      expect(result).toEqual([{leaf:`['name']`, value: 'foo'}]);
    });


    it('a simple json with multiple attributes', function () {
      let json = { name: 'foo', bar: 'baz'};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf: `['name']`, value: 'foo'}, {leaf:`['bar']`, value: 'baz'}]);
    });

    it('nested objects', function () {
      let json = { name: {foo: 'bar'}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['name']['foo']`, value: 'bar'}]);
    });

    it('nested objects with multivalues', function () {
      let json = { name: {foo: 'bar', baz: 2}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['name']['foo']`, value: 'bar'}, {leaf:`['name']['baz']`, value: 2}]);
    });

    it('deep nested objects', function () {
      let json = { name: { baz: { context: {foo: 2}}}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['name']['baz']['context']['foo']`, value: 2}]);
    });

    it('deep nested objects with multivalues', function () {
      let json = { name: { baz: { context: {foo: 2, boom: 'go'}}}};
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([ {leaf:`['name']['baz']['context']['foo']`, value: 2}, {leaf:`['name']['baz']['context']['boom']`, value: 'go'}]);
    });

    it('json with an array of strings', function () {
      let json = { name: ['foo'] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['name'][0]`, value: 'foo'}]);
    });

    it('json with an array of multiple strings', function () {
      let json = { name: ['foo', 'bar'] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['name'][0]`, value: 'foo'}, {leaf:`['name'][1]`, value: 'bar'}]);
    });

    it('json with an array of one object', function () {
      let json = { name: [{foo: 2}] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['name'][0]['foo']`, value: 2}]);
    });

    it('json with an array of two object', function () {
      let json = { name: [{foo: 2}, {foo:3}] };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['name'][1]['foo']`, value: 3}, {leaf:`['name'][0]['foo']`, value: 2}]);
    });

    it('json with dots in the key name', function () {
      let json = { 'notification.type': 'foo', 'deploy.server.group': {'server.group.name': 'baz'} };
      let result = builder.convertJsonKeysToBracketedList(json);
      expect(result).toEqual([{leaf:`['notification.type']`, value: 'foo'}, {leaf:`['deploy.server.group']['server.group.name']`, value: 'baz'}]);
    });

  });

  describe('create list with and exclude list', function () {
    it('should exclude the top task node from the list', function () {
      let json = { name: {foo: 2}, task:{bar: 3}};
      let ignoreList = ['task'];
      let result = builder.convertJsonKeysToBracketedList(json, ignoreList);
      expect(result).toEqual([{leaf:`['name']['foo']`, value: 2}]);
    });

    it('should include the task node deeper in the tree from the list', function () {
      let json = { name: {foo: 2}, joy: {task:{bar: 3}}};
      let ignoreList = ['name'];
      let result = builder.convertJsonKeysToBracketedList(json, ignoreList);
      expect(result).toEqual([{leaf:`['joy']['task']['bar']`, value: 3}]);
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
