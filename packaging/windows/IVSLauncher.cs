using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace IvsLauncher
{
    internal static class Program
    {
        private const string MutexName = @"Local\IVS.Desktop.Launcher";
        private const int PreferredPort = 8090;
        private const int MaxPort = 8199;
        private const int StartupTimeoutMs = 240000;

        [STAThread]
        private static int Main(string[] args)
        {
            string appDirectory = AppDomain.CurrentDomain.BaseDirectory;

            bool createdNew;
            using (var mutex = new Mutex(true, MutexName, out createdNew))
            {
                if (!createdNew)
                {
                    return WaitForRunningInstance();
                }

                try
                {
                    int existingPort = FindExistingServer();
                    if (existingPort > 0)
                    {
                        OpenBrowser(existingPort);
                        return 0;
                    }

                    string coreExecutable = Path.Combine(appDirectory, "IVS.exe");
                    if (!File.Exists(coreExecutable))
                    {
                        ShowError("未找到 IVS.exe，请重新安装容大进销存系统。");
                        return 1;
                    }

                    int port = FindFreePort();
                    if (port <= 0)
                    {
                        ShowError("未找到可用端口，请关闭其他程序后重试。");
                        return 1;
                    }

                    var startInfo = new ProcessStartInfo
                    {
                        FileName = coreExecutable,
                        Arguments = "--server.port=" + port + " --ivs.desktop.open-browser=false",
                        WorkingDirectory = appDirectory,
                        UseShellExecute = true
                    };

                    Process core = Process.Start(startInfo);
                    if (core == null)
                    {
                        ShowError("无法启动进销存系统核心程序。");
                        return 1;
                    }

                    var deadline = DateTime.UtcNow.AddMilliseconds(StartupTimeoutMs);
                    while (DateTime.UtcNow < deadline)
                    {
                        if (IsIvsServer(port))
                        {
                            SavePort(port);
                            OpenBrowser(port);
                            return 0;
                        }

                        if (core.HasExited)
                        {
                            WriteLog("IVS core exited early with code " + core.ExitCode + ".");
                            ShowError("进销存系统启动失败，错误代码：" + core.ExitCode + "。请重新安装后再试。");
                            return 1;
                        }

                        Thread.Sleep(700);
                    }

                    WriteLog("Startup timed out on port " + port + ".");
                    ShowError("进销存系统启动超时，请关闭已打开的系统后重新启动。");
                    return 1;
                }
                catch (Exception ex)
                {
                    WriteLog("Launcher exception: " + ex);
                    ShowError("进销存系统启动失败：" + ex.Message);
                    return 1;
                }
                finally
                {
                    try { mutex.ReleaseMutex(); } catch { }
                }
            }
        }

        private static int WaitForRunningInstance()
        {
            var deadline = DateTime.UtcNow.AddSeconds(45);
            while (DateTime.UtcNow < deadline)
            {
                int port = FindExistingServer();
                if (port > 0)
                {
                    OpenBrowser(port);
                    return 0;
                }
                Thread.Sleep(500);
            }

            ShowInfo("进销存系统正在启动，请稍候。启动完成后可再次双击桌面图标。");
            return 0;
        }

        private static int FindExistingServer()
        {
            int savedPort = ReadSavedPort();
            if (savedPort > 0 && IsIvsServer(savedPort))
            {
                return savedPort;
            }

            for (int port = PreferredPort; port <= PreferredPort + 10; port++)
            {
                if (IsIvsServer(port))
                {
                    return port;
                }
            }

            return -1;
        }

        private static bool IsIvsServer(int port)
        {
            try
            {
                var request = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:" + port + "/");
                request.Method = "GET";
                request.Proxy = null;
                request.Timeout = 700;
                request.ReadWriteTimeout = 700;
                request.KeepAlive = false;

                using (var response = (HttpWebResponse)request.GetResponse())
                using (var reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
                {
                    string body = reader.ReadToEnd();
                    return body.IndexOf("IVS", StringComparison.OrdinalIgnoreCase) >= 0
                        || body.IndexOf("进销存", StringComparison.Ordinal) >= 0;
                }
            }
            catch
            {
                return false;
            }
        }

        private static int FindFreePort()
        {
            for (int port = PreferredPort; port <= MaxPort; port++)
            {
                TcpListener listener = null;
                try
                {
                    listener = new TcpListener(IPAddress.Any, port);
                    listener.Start();
                    return port;
                }
                catch (SocketException)
                {
                }
                finally
                {
                    if (listener != null)
                    {
                        try { listener.Stop(); } catch { }
                    }
                }
            }

            return -1;
        }

        private static string PortFile
        {
            get
            {
                string directory = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "IVS");
                Directory.CreateDirectory(directory);
                return Path.Combine(directory, "desktop-port.txt");
            }
        }

        private static string LogFile
        {
            get
            {
                string directory = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "IVS");
                Directory.CreateDirectory(directory);
                return Path.Combine(directory, "launcher.log");
            }
        }

        private static int ReadSavedPort()
        {
            try
            {
                if (!File.Exists(PortFile)) return -1;
                string value = File.ReadAllText(PortFile).Trim();
                int port;
                return int.TryParse(value, out port) ? port : -1;
            }
            catch
            {
                return -1;
            }
        }

        private static void SavePort(int port)
        {
            try
            {
                File.WriteAllText(PortFile, port.ToString());
                WriteLog("IVS desktop server is ready on port " + port + ".");
            }
            catch (Exception ex)
            {
                WriteLog("Could not save desktop port: " + ex.Message);
            }
        }

        private static void OpenBrowser(int port)
        {
            if (Environment.GetEnvironmentVariable("IVS_LAUNCHER_TEST_NO_BROWSER") == "1")
            {
                WriteLog("Browser launch skipped by test setting for port " + port + ".");
                return;
            }

            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "http://127.0.0.1:" + port + "/",
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                WriteLog("Could not open browser: " + ex.Message);
                ShowError("系统已启动，但浏览器无法自动打开。请手动访问 http://127.0.0.1:" + port + "/");
            }
        }

        private static void WriteLog(string message)
        {
            try
            {
                File.AppendAllText(LogFile, DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " " + message + Environment.NewLine);
            }
            catch
            {
            }
        }

        private static void ShowError(string message)
        {
            MessageBox.Show(message, "容大进销存系统", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }

        private static void ShowInfo(string message)
        {
            MessageBox.Show(message, "容大进销存系统", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
    }
}