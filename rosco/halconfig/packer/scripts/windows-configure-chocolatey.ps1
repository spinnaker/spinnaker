# Update PS to allow TLS1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

# Install Chocolatey.
Invoke-WebRequest https://chocolatey.org/install.ps1 -UseBasicParsing | Invoke-Expression

# These are the reserved sources that are handled directly by Chocolatey.
$alternateSources = @("ruby", "webpi", "cygwin", "windowsfeatures", "python")

# Spilt supplied repositories into individual entries.
[string]$chocolateyRepository = $env:repository
[string[]]$repositories = @()
if (![string]::IsNullOrWhiteSpace($chocolateyRepository) -AND $chocolateyRepository -contains ';') {
    [string[]]$repositories = $chocolateyRepository.Split(';', [System.StringSplitOptions]::RemoveEmptyEntries)
} else {
    $repositories = @($chocolateyRepository)
}

# Process each repository, adding each one as a package repository.
$repositoryPriority = 0
for ($i=0; $i -lt $repositories.Length; $i++) {

    if (![string]::IsNullOrWhiteSpace($repositories[$i])) {

        # Remove leading and trailing whitespaces from the repository URL.
        $repository = $repositories[$i].Trim()

        if (![string]::IsNullOrWhiteSpace($repository)) {

            $user = $null
            $pass = $null

            # Ignore repository if it's one of the alternate sources.
            if ($alternateSources -contains $repository) { continue }

            # Validate the repository URL exists. If status code is not 200 or an exception is
            # thrown, ignore the repository.
            $isValid = $false
            try {

                $repoUri = [System.Uri]$repository
                $headers = @{}
                if(![string]::IsNullOrWhiteSpace($repoUri.UserInfo))
                {
                    $userpass = $repoUri.UserInfo.Split(":")
                    $user = $userpass[0]
                    $pass = $userpass[1]
                    $repository = $repoUri.AbsoluteUri.Replace($repoUri.UserInfo + "@", "")
                    $base64 = [System.Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("${user}:${pass}"))
                    $headers = @{ Authorization = "Basic $base64" }
                }

                $statusCode = (Invoke-WebRequest -Uri "$repository" `
                                                 -UseBasicParsing `
                                                 -Method GET `
                                                 -MaximumRedirection 0 `
                                                 -TimeoutSec 60 `
                                                 -Headers $headers).StatusCode
                $isValid = ($statusCode -ge 200 -and $statusCode -lt 300)

            } catch {
                $ErrorMessage = $_.Exception.Message
                $FailedItem = $_.Exception.ItemName
                Write-Host "Caught exception validating repository ($$repository{}). FailedItem: $FailedItem; ErrorMessage: $ErrorMessage"
                continue
            }

            # Ignore repository if URL is invalid.
            if (!$isValid) { continue }

            # Increment repository priority.
            $repositoryPriority++

            # Extract the repository name. Use the hostname and domain, coverting the periods to dashes.
            $repositoryName = (New-Object System.Uri $repository).Host.Replace(".", "-")

            # Add repository to Chocolatey.
            if(![string]::IsNullOrWhiteSpace($user)){
                &"choco" source add --yes --name=$repositoryName --source=`"$repository`" --priority=$repositoryPriority --user=$user --password=$pass
            } else {
                &"choco" source add --yes --name=$repositoryName --source=`"$repository`" --priority=$repositoryPriority
            }

        }
    }
}
