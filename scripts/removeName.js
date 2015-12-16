module.exports = function(file, api) {
  const j = api.jscodeshift;
  const { expression, statement, statements } = j.template;

  return j(file.source)
    .find(j.MemberExpression, {
      property: {
        name: "name"
      },
    })
    .filter(
      p => p.parent.value.type == "AssignmentExpression" &&
      p.parent.value.left.property.name == "exports"
    )

    .replaceWith(
      p => p.node.object
    )
    .toSource();
};
