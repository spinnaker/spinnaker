const fs = require('fs');
const { set, unset } = require('lodash');
const stripJsonComments = require('strip-json-comments');

function readJson(pathname) {
  const string = fs.readFileSync(pathname).toString();
  return JSON.parse(stripJsonComments(string));
}

function writeJson(pathname, json) {
  fs.writeFileSync(pathname, JSON.stringify(json, null, 2));
}

function deleteJsonField(filename, field, val) {
  const json = readJson(filename);
  unset(json, field);
  writeJson(filename, json);
}

function writeJsonField(filename, field, val) {
  const json = readJson(filename);
  set(json, field, val);
  writeJson(filename, json);
}

module.exports = { readJson, writeJson, writeJsonField, deleteJsonField };
