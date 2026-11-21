using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Text;
using System.Windows.Forms;

namespace IvsLanClient
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new LoginForm());
        }
    }

    public sealed class LoginForm : Form
    {
        private readonly TextBox serverText = new TextBox();
        private readonly Label statusLabel = new Label();

        public LoginForm()
        {
            Text = "容大进销存系统 - 局域网登录";
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ClientSize = new Size(560, 285);
            Font = new Font("Microsoft YaHei UI", 9F);

            var title = new Label
            {
                Text = "局域网登录",
                Font = new Font("Microsoft YaHei UI", 16F, FontStyle.Bold),
                AutoSize = true,
                Location = new Point(28, 22)
            };
            Controls.Add(title);

            var description = new Label
            {
                Text = "连接主机端正在运行的进销存服务。账号必须由主机管理员在主机系统内创建。",
                AutoSize = false,
                Size = new Size(500, 40),
                Location = new Point(31, 61),
                ForeColor = Color.FromArgb(80, 90, 105)
            };
            Controls.Add(description);

            var addressLabel = new Label
            {
                Text = "主机服务器地址",
                AutoSize = true,
                Location = new Point(31, 112)
            };
            Controls.Add(addressLabel);

            serverText.Location = new Point(31, 137);
            serverText.Size = new Size(498, 28);
            Controls.Add(serverText);

            var testButton = new Button
            {
                Text = "测试连接",
                Location = new Point(31, 178),
                Size = new Size(112, 34)
            };
            testButton.Click += TestButtonClick;
            Controls.Add(testButton);

            var openButton = new Button
            {
                Text = "打开登录页面",
                Location = new Point(151, 178),
                Size = new Size(136, 34)
            };
            openButton.Click += OpenButtonClick;
            Controls.Add(openButton);

            var diagnosticButton = new Button
            {
                Text = "网络诊断",
                Location = new Point(295, 178),
                Size = new Size(112, 34)
            };
            diagnosticButton.Click += DiagnosticButtonClick;
            Controls.Add(diagnosticButton);

            var exitButton = new Button
            {
                Text = "退出",
                Location = new Point(417, 178),
                Size = new Size(112, 34)
            };
            exitButton.Click += delegate { Close(); };
            Controls.Add(exitButton);

            statusLabel.AutoSize = false;
            statusLabel.Size = new Size(498, 42);
            statusLabel.Location = new Point(31, 225);
            statusLabel.ForeColor = Color.FromArgb(80, 90, 105);
            statusLabel.Text = "正在读取服务器地址...";
            Controls.Add(statusLabel);

            Load += delegate
            {
                serverText.Text = LoadServer();
                statusLabel.Text = "请确认地址指向主机电脑，例如 http://192.168.1.101:8090/";
            };
        }

        private string ConfigFile
        {
            get { return Path.Combine(Application.StartupPath, "服务器地址.txt"); }
        }

        private string LoadServer()
        {
            try
            {
                if (File.Exists(ConfigFile))
                {
                    string value = NormalizeServer(File.ReadAllText(ConfigFile).Trim());
                    if (value.Length > 0) return value;
                }
            }
            catch
            {
            }
            return "http://127.0.0.1:8090/";
        }

        private static string NormalizeServer(string value)
        {
            string text = value == null ? "" : value.Trim();
            if (text.Length == 0) return "";
            if (text.IndexOf("://", StringComparison.Ordinal) < 0) text = "http://" + text;
            Uri uri;
            if (!Uri.TryCreate(text, UriKind.Absolute, out uri)) return "";
            if (!string.Equals(uri.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase)
                && !string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)) return "";
            string result = uri.GetLeftPart(UriPartial.Query);
            if (!result.EndsWith("/", StringComparison.Ordinal)
                && result.IndexOf("?", StringComparison.Ordinal) < 0) result += "/";
            return result;
        }

        private void SaveServer(string value)
        {
            try
            {
                File.WriteAllText(ConfigFile, value + Environment.NewLine, new UTF8Encoding(false));
            }
            catch (Exception ex)
            {
                MessageBox.Show("服务器地址无法保存：" + ex.Message, "容大进销存系统", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private bool ValidateAndSaveServer()
        {
            string server = NormalizeServer(serverText.Text);
            if (server.Length == 0)
            {
                MessageBox.Show("请输入有效的主机服务器地址，例如 http://192.168.1.101:8090/", "地址格式错误", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                serverText.Focus();
                return false;
            }
            serverText.Text = server;
            SaveServer(server);
            return true;
        }

        private void TestButtonClick(object sender, EventArgs e)
        {
            if (!ValidateAndSaveServer()) return;
            string server = NormalizeServer(serverText.Text);
            Cursor = Cursors.WaitCursor;
            statusLabel.ForeColor = Color.FromArgb(80, 90, 105);
            statusLabel.Text = "正在测试主机连接...";
            Application.DoEvents();
            try
            {
                string body = ReadHealth(server);
                bool ok = body.IndexOf("\"UP\"", StringComparison.OrdinalIgnoreCase) >= 0;
                statusLabel.ForeColor = ok ? Color.SeaGreen : Color.Firebrick;
                statusLabel.Text = ok
                    ? "连接成功，主机服务正常。可直接打开登录页面。"
                    : "主机可以访问，但服务尚未就绪。请在主机运行“主机端-一键开启局域网访问”。";
            }
            catch (Exception ex)
            {
                statusLabel.ForeColor = Color.Firebrick;
                statusLabel.Text = "连接失败。请检查主机是否开机、地址是否正确、双方是否在同一局域网。";
                MessageBox.Show(statusLabel.Text + Environment.NewLine + Environment.NewLine + ex.Message,
                    "连接失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally
            {
                Cursor = Cursors.Default;
            }
        }

        private void OpenButtonClick(object sender, EventArgs e)
        {
            if (!ValidateAndSaveServer()) return;
            string server = NormalizeServer(serverText.Text);
            try
            {
                string body = ReadHealth(server);
                if (body.IndexOf("\"UP\"", StringComparison.OrdinalIgnoreCase) < 0)
                {
                    MessageBox.Show("主机服务尚未就绪，请先在主机运行“主机端-一键开启局域网访问”。",
                        "暂时无法登录", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return;
                }
            }
            catch (Exception ex)
            {
                DialogResult answer = MessageBox.Show(
                    "无法连接该服务器。是否仍然尝试打开登录页面？" + Environment.NewLine + Environment.NewLine + ex.Message,
                    "连接检查失败", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
                if (answer != DialogResult.Yes) return;
            }

            try
            {
                Process.Start(new ProcessStartInfo { FileName = server, UseShellExecute = true });
            }
            catch (Exception ex)
            {
                MessageBox.Show("浏览器无法打开：" + ex.Message + Environment.NewLine + "请手动复制地址到浏览器：" + server,
                    "打开失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void DiagnosticButtonClick(object sender, EventArgs e)
        {
            string script = Path.Combine(Application.StartupPath, "局域网连接诊断.ps1");
            if (!File.Exists(script))
            {
                MessageBox.Show("未找到局域网连接诊断.ps1", "诊断工具缺失", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            Process.Start(new ProcessStartInfo
            {
                FileName = "powershell.exe",
                Arguments = "-NoProfile -ExecutionPolicy Bypass -File \"" + script + "\"",
                WorkingDirectory = Application.StartupPath,
                UseShellExecute = true
            });
        }

        private static string ReadHealth(string server)
        {
            Uri baseUri = new Uri(server, UriKind.Absolute);
            Uri healthUri = new Uri(baseUri, "actuator/health");
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(healthUri);
            request.Method = "GET";
            request.Proxy = null;
            request.Timeout = 7000;
            request.ReadWriteTimeout = 7000;
            request.KeepAlive = false;
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (StreamReader reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
            {
                return reader.ReadToEnd();
            }
        }
    }
}