for /F "tokens=2" %%K in ('
   tasklist /FI "ImageName eq nomad.exe" /FO LIST ^| findstr /B "PID:"
') do (
   start /B C:\opt\radix\timberland\consul-template\windows-kill.exe -SIGBREAK %%K
)
exit /b 0
