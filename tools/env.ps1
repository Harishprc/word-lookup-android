# Sets the toolchain for this project. Dot-source it:  . .\tools\env.ps1
$env:JAVA_HOME    = 'C:\tools\jdk\jdk-17.0.20+8'
$env:ANDROID_HOME = 'C:\tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;C:\tools\gradle\gradle-8.9\bin;C:\tools\jadx\bin;$env:PATH"
