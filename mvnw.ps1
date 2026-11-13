param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = "$scriptDir\tools\jdk-21.0.13+9"
$mvnCmd = "$scriptDir\tools\apache-maven-3.9.6\bin\mvn.cmd"
& $mvnCmd @MavenArgs

