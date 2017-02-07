# The EC2Config Service configures the EC2 instance at boot time. See the following URL for more details:
# http://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/UsingConfig_WinAMI.html#UsingConfigXML_WinAMI

# Note: The EC2Config Service is not included in Windows Server 2016 AMI's. See the folloing URL for more details:
# http://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/windows-ami-version-history.html#win2k16-amis

# Configure the EC2Config service.
$ConfigFile="C:\\Program Files\\Amazon\\Ec2ConfigService\\Settings\\Config.xml"
$xml = [xml](get-content $ConfigFile)
$xmlElement = $xml.get_DocumentElement()
$xmlElementToModify = $xmlElement.Plugins

foreach ($element in $xmlElementToModify.Plugin)
{
    if ($element.name -eq "Ec2SetPassword")
    {
        $element.State="Enabled"
    }
    elseif ($element.name -eq "Ec2SetComputerName")
    {
        $element.State="Enabled"
    }
    elseif ($element.name -eq "Ec2HandleUserData")
    {
        $element.State="Enabled"
    }
}
$xml.Save($ConfigFile)

# Configure how EC2Config prepares the instance for AMI creation.
$BundleConfigFile="C:\\Program Files\\Amazon\\Ec2ConfigService\\Settings\\BundleConfig.xml"
$xml = [xml](get-content $BundleConfigFile)
$xmlElement = $xml.get_DocumentElement()

foreach ($element in $xmlElement.Property)
{
    if ($element.Name -eq "AutoSysprep")
    {
        $element.Value="Yes"
    }
}
$xml.Save($BundleConfigFile)
