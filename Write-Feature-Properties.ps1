<#
.SYNOPSIS
    Retrieves the list of feature branches and writes them to a property file of the form
    Features=feature_xxxx,feaure_yyyy,feature_zzzz  

.EXAMPLE
    Write-FeatureProperties -GitUrl https://user:password@github.com/revdotcom/revdotcom -OutFile branches.properties
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$True)] [string] $GitUrl,
    [Parameter(Mandatory=$True)] [string] $OutFile
)

$branches = git ls-remote --heads $GitUrl
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$features = $branches | ? { $_ -match "\s+refs/heads/feature/(.*)$" } | % { "feature_" + $matches[1] } 
$features = $features -Join ','
#use ASCII to avoid Out-File writing a Byte Order Mark
echo Features=$features | Out-File -Encoding ascii $OutFile
