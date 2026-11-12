[CmdletBinding()]
param(
  [string]$SourcePath = (Join-Path $PSScriptRoot '..\logo.jpg'),
  [string]$WindowsIconDirectory = (Join-Path $PSScriptRoot '..\packaging\windows'),
  [string]$FrontendPublicDirectory = (Join-Path $PSScriptRoot '..\frontend\public')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$sourcePathResolved = (Resolve-Path -LiteralPath $SourcePath).Path
if (-not (Test-Path -LiteralPath $WindowsIconDirectory)) {
  New-Item -ItemType Directory -Path $WindowsIconDirectory | Out-Null
}
if (-not (Test-Path -LiteralPath $FrontendPublicDirectory)) {
  New-Item -ItemType Directory -Path $FrontendPublicDirectory | Out-Null
}

$source = [System.Drawing.Bitmap]::new($sourcePathResolved)
try {
  if ($source.Width -lt 394 -or $source.Height -lt 308) {
    throw "Logo image is too small. Expected at least 394x308, got $($source.Width)x$($source.Height)."
  }

  # The source has the graphic from x=163..367, y=69..286 and separate lettering below.
  # This square crop keeps only the upper graphic with balanced breathing room.
  $crop = [System.Drawing.Rectangle]::new(135, 48, 260, 260)
  $masterSize = 512
  $master = [System.Drawing.Bitmap]::new(
    $masterSize,
    $masterSize,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
  )

  try {
    $graphics = [System.Drawing.Graphics]::FromImage($master)
    try {
      $graphics.Clear([System.Drawing.Color]::Transparent)
      $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
      $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
      $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
      $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
      $destination = [System.Drawing.Rectangle]::new(0, 0, $masterSize, $masterSize)
      $graphics.DrawImage(
        $source,
        $destination,
        $crop,
        [System.Drawing.GraphicsUnit]::Pixel
      )
    } finally {
      $graphics.Dispose()
    }

    # Convert the white JPG background to transparency while un-premultiplying
    # anti-aliased edge colors so the mark stays crisp on light and dark shells.
    for ($y = 0; $y -lt $masterSize; $y++) {
      for ($x = 0; $x -lt $masterSize; $x++) {
        $color = $master.GetPixel($x, $y)
        $minimum = [Math]::Min($color.R, [Math]::Min($color.G, $color.B))
        $alpha = [int][Math]::Round(255 * (1 - ($minimum / 255.0)))
        if ($alpha -le 3) {
          $master.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
          continue
        }

        $red = [int][Math]::Round((($color.R - 255.0) * 255.0 / $alpha) + 255.0)
        $green = [int][Math]::Round((($color.G - 255.0) * 255.0 / $alpha) + 255.0)
        $blue = [int][Math]::Round((($color.B - 255.0) * 255.0 / $alpha) + 255.0)
        $red = [Math]::Max(0, [Math]::Min(255, $red))
        $green = [Math]::Max(0, [Math]::Min(255, $green))
        $blue = [Math]::Max(0, [Math]::Min(255, $blue))
        $master.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, $red, $green, $blue))
      }
    }

    $pngPath = Join-Path $WindowsIconDirectory 'IVS.png'
    $master.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)

    $sizes = @(16, 24, 32, 48, 64, 128, 256)
    $images = [System.Collections.Generic.List[byte[]]]::new()
    foreach ($size in $sizes) {
      $image = [System.Drawing.Bitmap]::new(
        $size,
        $size,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
      )
      try {
        $graphics = [System.Drawing.Graphics]::FromImage($image)
        try {
          $graphics.Clear([System.Drawing.Color]::Transparent)
          $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
          $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
          $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
          $graphics.DrawImage($master, 0, 0, $size, $size)
        } finally {
          $graphics.Dispose()
        }

        $stream = [System.IO.MemoryStream]::new()
        try {
          $image.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
          $images.Add($stream.ToArray())
        } finally {
          $stream.Dispose()
        }
      } finally {
        $image.Dispose()
      }
    }

    $icoPath = Join-Path $WindowsIconDirectory 'IVS.ico'
    $file = [System.IO.File]::Create($icoPath)
    try {
      $writer = [System.IO.BinaryWriter]::new($file)
      try {
        $writer.Write([uint16]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]$sizes.Count)
        $offset = 6 + (16 * $sizes.Count)
        for ($i = 0; $i -lt $sizes.Count; $i++) {
          $size = $sizes[$i]
          $dimensionByte = if ($size -ge 256) { 0 } else { $size }
          $writer.Write([byte]$dimensionByte)
          $writer.Write([byte]$dimensionByte)
          $writer.Write([byte]0)
          $writer.Write([byte]0)
          $writer.Write([uint16]1)
          $writer.Write([uint16]32)
          $writer.Write([uint32]$images[$i].Length)
          $writer.Write([uint32]$offset)
          $offset += $images[$i].Length
        }
        foreach ($bytes in $images) {
          $writer.Write($bytes)
        }
      } finally {
        $writer.Dispose()
      }
    } finally {
      $file.Dispose()
    }

    Copy-Item -LiteralPath $pngPath -Destination (Join-Path $FrontendPublicDirectory 'logo.png') -Force
    Copy-Item -LiteralPath $icoPath -Destination (Join-Path $FrontendPublicDirectory 'favicon.ico') -Force

    $icon = [System.Drawing.Icon]::new($icoPath)
    try {
      Write-Output "Generated $icoPath ($($icon.Width)x$($icon.Height), $($sizes.Count) sizes)"
    } finally {
      $icon.Dispose()
    }
    Write-Output "Generated $pngPath"
  } finally {
    $master.Dispose()
  }
} finally {
  $source.Dispose()
}
