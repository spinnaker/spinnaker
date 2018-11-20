def GetBaseBranch(event):
    return event.payload.get('pull_request', {}).get('base', {}).get('ref', None)

def GetTitle(event):
    return event.payload.get('pull_request', {}).get('title', {})

def GetRepo(event):
    repo = event.payload.get('pull_request', {}).get('base', {}).get('repo', {}).get('full_name')
    if repo is None:
        url = event.payload.get('issue', {}).get('url')
        if not url:
            return None

        return '/'.join(url.split('/')[-4:-2])
    return repo
    
def GetPullRequest(g, event):
    repo = GetRepo(event)
    number = event.payload.get('number', event.payload.get('issue', {}).get('number'))
    if repo == None or number == None:
        url = event.payload.get('issue', {}).get('url')
        if not url:
            return None

        number = int(url.split('/')[-1])

        if repo == None or number == None:
            return None

    return g.get_pull_request(repo, number)
