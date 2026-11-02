#define MyAppName "IVS 进销存系统"
#define MyAppVersion GetEnv("APP_VERSION")
#define MyAppExeName "IVS.exe"

[Setup]
AppId={{A7F4C34D-8E7D-4BD0-9B29-3C2D27EA8C52}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=wang66676
AppPublisherURL=https://github.com/wang66676/ivs-inventory-system
SourceDir=..\..
DefaultDirName={localappdata}\Programs\IVS
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=dist-setup
OutputBaseFilename=IVS-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
UninstallDisplayIcon={app}\{#MyAppExeName}

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加任务："

[Files]
Source: "windows-app\IVS\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "packaging\web\网页登录版.url"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\IVS 进销存系统"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{group}\网页登录版"; Filename: "{app}\网页登录版.url"
Name: "{autodesktop}\IVS 进销存系统"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Description: "立即运行 IVS 进销存系统"; Flags: nowait postinstall skipifsilent

[Code]
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Exec('taskkill.exe', '/IM IVS.exe /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Result := '';
end;
