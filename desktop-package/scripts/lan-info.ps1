$ErrorActionPreference = 'Continue'
Write-Host '本机访问：http://localhost:8090'
Write-Host '局域网访问地址：'
try {
  Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object {
    $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*'
  } | ForEach-Object { Write-Host ("  http://" + $_.IPAddress + ":8090") }
} catch {
  [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
    Where-Object { $_.AddressFamily -eq 'InterNetwork' -and $_.IPAddressToString -notlike '127.*' } |
    ForEach-Object { Write-Host ("  http://" + $_.IPAddressToString + ":8090") }
}
