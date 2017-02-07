$packages = @()
if (![string]::IsNullOrWhiteSpace($env:packages)) {

    # Split supplied packages into an array.
    $packages = $env:packages.replace(',', ' ').split(' ')

    # Install packages and enforce installation order.
    foreach ($package in $packages) {
        if (![string]::IsNullOrWhiteSpace($package)) {
            &"choco" install $package --yes
        }
    }
}
