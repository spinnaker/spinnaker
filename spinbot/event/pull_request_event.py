def GetBaseBranch(event):
    return event.payload.get('pull_request', {}).get('base', {}).get('ref', None)

def GetTitle(event):
    return event.payload.get('pull_request', {}).get('title', {})

def GetRepo(event):
    return event.payload.get('pull_request', {}).get('base', {}).get('repo', {}).get('full_name')
    
def GetPullRequest(g, event):
    repo = GetRepo(event)
    number = event.payload.get('number')
    if repo == None or number == None:
        return None

    return g.get_pull_request(repo, number)
