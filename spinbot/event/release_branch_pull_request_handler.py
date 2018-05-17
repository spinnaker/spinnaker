from .handler import Handler
from .pull_request_event import GetBaseBranch, GetPullRequest, GetTitle
from gh import ReleaseBranchFor, ParseCommitMessage

close_message = ('Only fixes can be merged into ' + 
    'release branches. This is currently tagged as "{}" which doesn\'t qualify.\n\nRead more ' +
    'about [commit conventions](https://www.spinnaker.io/community/contributing/submitting/#commit-message-conventions) ' +
    'and [patch releases](https://www.spinnaker.io/community/releases/release-cadence/#patching-the-release-candidate) here.')

class ReleaseBranchPullRequestHandler(Handler):
    def __init__(self):
        super().__init__()

    def handles(self, event):
        return (event.type == 'PullRequestEvent'
            and event.payload.get('action') == 'opened'
            and ReleaseBranchFor(GetBaseBranch(event)) != None)

    def handle(self, g, event):
        message = ParseCommitMessage(GetTitle(event))
        if message is None:
            message = {}

        if message.get('type') != 'fix':
            pull_request = GetPullRequest(g, event)
            if pull_request is None:
                self.logging.warn('Unable to determine PR that created {}'.format(event))
                return

            pull_request.create_issue_comment(close_message.format(message.get('type')))
            pull_request.edit(state='closed')

ReleaseBranchPullRequestHandler()
