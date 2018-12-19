"""Support for building and spinnaker releases."""

# pylint: disable=wrong-import-position

# These would be required if running from source code
SPINNAKER_RUNNABLE_REPOSITORY_NAMES = [
    'clouddriver',
    'deck',
    'echo', 'fiat', 'front50',
    'gate', 'igor', 'kayenta', 'orca', 'rosco']

# For building and validating a release
SPINNAKER_PROCESS_REPOSITORY_NAMES = ['spinnaker']

# Halyard is independent of spinnaker
SPINNAKER_HALYARD_REPOSITORY_NAME = 'halyard'

# Documentation is version agnostic
SPINNAKER_GITHUB_IO_REPOSITORY_NAME = 'spinnaker.github.io'


from buildtool.util import (
    DEFAULT_BUILD_NUMBER,
    add_parser_argument,
    unused_port,

    log_timestring,
    timedelta_string,
    log_embedded_output,

    ensure_dir_exists,
    write_to_path)

from buildtool.errors import (
    BuildtoolError,
    ConfigError,
    ExecutionError,
    ResponseError,
    TimeoutError,
    UnexpectedError,

    exception_to_message,
    maybe_log_exception,
    raise_and_log_error,

    check_kwargs_empty,
    check_options_set,
    check_path_exists,

    # This is very specialized, but here to share
    # between validate_bom__deploy and image_commands
    scan_logs_for_install_errors)

from buildtool.subprocess_support import (
    start_subprocess,
    wait_subprocess,
    run_subprocess,
    check_subprocess,
    check_subprocess_sequence,
    run_subprocess_sequence,
    check_subprocesses_to_logfile,
    determine_subprocess_outcome_labels)

from buildtool.git_support import (
    GitRepositorySpec,
    GitRunner,

    CommitMessage,
    CommitTag,
    RepositorySummary,
    SemanticVersion)

from buildtool.hal_support import (
    HalRunner)

from buildtool.scm import (
    SourceInfo,
    SpinnakerSourceCodeManager)

from buildtool.bom_scm import (
    SPINNAKER_BOM_REPOSITORY_NAMES,
    BomSourceCodeManager)

from buildtool.branch_scm import (
    BranchSourceCodeManager)

from buildtool.command import (
    CommandFactory,
    CommandProcessor)

from buildtool.repository_command import (
    RepositoryCommandProcessor,
    RepositoryCommandFactory)

from buildtool.gradle_support import (
    GradleCommandFactory,
    GradleCommandProcessor,
    GradleRunner)

from buildtool.metrics import MetricsManager
