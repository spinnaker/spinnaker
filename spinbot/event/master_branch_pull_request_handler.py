from .handler import Handler
from .pull_request_event import GetBaseBranch, GetPullRequest, GetTitle
from gh import ReleaseBranchFor, ParseCommitMessage

format_message = ('Please format your commit/pull request title into the form: \n\n' +
    '`<type>(<scope>): <subject>`, e.g. `fix(kubernetes): address NPE in status check`\n\n' +
    'This allows us to easily generate changelogs & determine semantic version numbers when ' +
    'cutting releases. You can read more about [commit ' +
    'conventions](https://www.spinnaker.io/community/contributing/submitting/#commit-message-conventions) ' +
    'here.')

class MasterBranchPullRequestHandler(Handler):
    def __init__(self):
        super().__init__()

    def handles(self, event):
        return (event.type == 'PullRequestEvent'
            and event.payload.get('action') == 'opened'
            and GetBaseBranch(event) == 'master')

    def handle(self, g, event):
        message = ParseCommitMessage(GetTitle(event))
        if message is None:
            pull_request = GetPullRequest(g, event)
            if pull_request is None:
                self.logging.warn('Unable to determine PR that created {}'.format(event))
                return

            pull_request.create_issue_comment(format_message)

MasterBranchPullRequestHandler()
