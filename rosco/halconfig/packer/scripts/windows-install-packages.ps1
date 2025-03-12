$packages = @()

function Convert-PackageNameToInstallablePackage([string]$packageName) {
    # Parse package name into it's constituent parts
    [string]$packageVersion = ""
    [string]$packageRelease = ""

    # Keep this verbatim minus leading bang
    if ($packageName.StartsWith("!")) {
      return $packageName.TrimStart("!"), ""
    }

    # Split packageName into an array.
    [Collections.Generic.List[String]]$parts = $packageName.split('.')

    if ($parts.Count -gt 2) {
        [int]$versionStart = 0

        for ([int]$i = 0; $i -lt $parts.Count; $i++) {
            if ($i -gt 0 -and $parts[$i] -match '^\d+$') {
                $versionStart = $i
                break
            }
        }

        if ($versionStart -lt 1) {
            for ([int]$i = 0; $i -lt $partsCount; $i++) {
                if ($i -gt 0 -and $parts[$i].Contains('-') -and $parts[$i][0] -match '^\d+$') {
                    $versionStart = $i
                    break
                }
            }
        }

        if ($versionStart -gt 0) {
            $packageName = [String]::Join('.', $parts.GetRange(0, $versionStart).ToArray())
            $packageVersion = [String]::Join('.', $parts.GetRange($versionStart, $parts.Count - $versionStart).ToArray())

            if ($packageVersion.Contains('-')) {
                ($packageVersion, $packageRelease) = $packageVersion.Split('-', 2)
            }
            elseif ($packageVersion.Contains('+')) {
                ($packageVersion, $packageRelease) = $packageVersion.Split('+', 2)
                $packageRelease = '+' + $packageRelease
            }

        }
        else {

            [int]$metaDataIndex = $parts.FindIndex( {$args[0] -match '\+'} )

            if ($metaDataIndex -gt -1) {
                ($packageName, $packageRelease) = $parts[$metaDataIndex].split('+')
                $packageRelease = "+" + $packageRelease
                $packageName = "${parts.subList(0, metaDataIndex).join('.')}.$name"
            }
            else {
                $packageName = [String]::Join('.', $parts)
            }
        }
    }
    elseif ($parts.Count -eq 2) {
        if ($parts[1] -match '^\d+$') {
            $packageName = $parts[0]
            $packageVersion = $parts[1]
        }
        elseif ($parts[1][0] -match '^\d+$' -and $parts[1].Contains('-')) {

            $packageName = $parts[0]

            $versionParts = $parts[1].Split('-', 2)
            $packageVersion = $versionParts[0]
            $packageRelease = $versionParts[1]

        }
        else {
            $packageName = [String]::Join('.', $parts)
        }
    }

    return $packageName, $packageVersion
}

function Install-AllPackages($packageNames){
  # Split supplied packages into an array.
  $packages = $packageNames.replace(',', ' ').split(' ')
  
    # Install packages and enforce installation order.
    foreach ($packageName in $packages | Where-Object {-not [string]::IsNullOrWhiteSpace($_)}) {
      $packageName, $packageVersion = Convert-PackageNameToInstallablePackage $packageName

      if (![string]::IsNullOrWhiteSpace($packageVersion)) {
        &"choco" install $packageName --yes --version $packageVersion
      }
      else {
        &"choco" install $packageName --yes
      }
      
      if ($LastExitCode -ne 0) {
        exit $LastExitCode
      }
    }
}

Write-Host $env:packages

if (![string]::IsNullOrWhiteSpace($env:packages)) {
  Install-AllPackages $env:packages
}
