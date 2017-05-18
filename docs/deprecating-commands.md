# Deprecating Commands

To deprecate a `hal` command, implement the `DeprecatedCommand` interface
and fill the `getDeprecatedWarning()` method with either a command to use 
in favor of your deprecated command, or another migration path. Users 
will see this message when using the deprecated command, so please allow
several stable releases to occur before fully removing the command.