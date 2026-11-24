using System;
using System.Drawing;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Forms;

namespace IvsLicenseRegistrar
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new RegistrarForm());
        }
    }

    internal sealed class RegistrarForm : Form
    {
        private readonly TextBox machineCode = new TextBox();
        private readonly TextBox customer = new TextBox();
        private readonly DateTimePicker expiry = new DateTimePicker();
        private readonly TextBox activationCode = new TextBox();
        private readonly Label status = new Label();

        public RegistrarForm()
        {
            Text = "容大进销存离线注册器";
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(720, 500);
            MinimumSize = new Size(680, 460);
            Font = new Font("Microsoft YaHei UI", 9F);

            var title = new Label
            {
                Text = "容大进销存系统 · 离线授权注册器",
                Font = new Font(Font.FontFamily, 16F, FontStyle.Bold),
                AutoSize = true,
                Location = new Point(24, 20)
            };
            var tip = new Label
            {
                Text = "输入客户软件登录页显示的安装码，选择授权到期日，生成注册码。私钥文件 license-private.xml 必须与本程序放在同一目录，禁止外传。",
                ForeColor = Color.FromArgb(92, 107, 130),
                AutoSize = false,
                Size = new Size(670, 42),
                Location = new Point(26, 58)
            };

            AddLabel("本机安装码", 28, 116);
            machineCode.Location = new Point(150, 112);
            machineCode.Size = new Size(535, 28);
            machineCode.Font = new Font("Consolas", 10F);

            AddLabel("授权客户", 28, 160);
            customer.Location = new Point(150, 156);
            customer.Size = new Size(300, 28);
            customer.Text = "容大进销存用户";

            AddLabel("授权到期", 28, 204);
            expiry.Location = new Point(150, 200);
            expiry.Size = new Size(180, 28);
            expiry.Format = DateTimePickerFormat.Custom;
            expiry.CustomFormat = "yyyy-MM-dd";
            expiry.Value = DateTime.Today.AddYears(1);

            var generate = new Button
            {
                Text = "生成注册码",
                Location = new Point(470, 198),
                Size = new Size(210, 34),
                BackColor = Color.FromArgb(22, 119, 255),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat
            };
            generate.Click += (sender, args) => Generate();

            AddLabel("注册码", 28, 252);
            activationCode.Location = new Point(28, 278);
            activationCode.Multiline = true;
            activationCode.ScrollBars = ScrollBars.Vertical;
            activationCode.ReadOnly = true;
            activationCode.Size = new Size(657, 125);
            activationCode.Font = new Font("Consolas", 9F);

            var copy = new Button
            {
                Text = "复制注册码",
                Location = new Point(28, 416),
                Size = new Size(130, 34)
            };
            copy.Click += (sender, args) =>
            {
                if (activationCode.TextLength == 0) return;
                Clipboard.SetText(activationCode.Text);
                status.Text = "注册码已复制到剪贴板。";
            };

            var close = new Button
            {
                Text = "关闭",
                Location = new Point(555, 416),
                Size = new Size(130, 34)
            };
            close.Click += (sender, args) => Close();

            status.Location = new Point(176, 424);
            status.Size = new Size(363, 24);
            status.ForeColor = Color.FromArgb(56, 158, 13);
            status.Text = "等待生成。";

            Controls.AddRange(new Control[] { title, tip, machineCode, customer, expiry, generate,
                activationCode, copy, close, status });
        }

        private void AddLabel(string text, int x, int y)
        {
            Controls.Add(new Label { Text = text, Location = new Point(x, y), AutoSize = true });
        }

        private void Generate()
        {
            try
            {
                string machine = machineCode.Text.Trim().ToUpperInvariant();
                Guid installationId;
                if (!Guid.TryParse(machine, out installationId))
                    throw new InvalidOperationException("安装码格式不正确。");

                string privateKeyPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "license-private.xml");
                if (!File.Exists(privateKeyPath))
                    throw new InvalidOperationException("未找到 license-private.xml。请把私钥文件与本程序放在同一目录。");

                DateTime localExpiry = expiry.Value.Date.AddDays(1).AddSeconds(-1);
                long expiryEpoch = new DateTimeOffset(localExpiry).ToUnixTimeSeconds();
                string customerText = string.IsNullOrWhiteSpace(customer.Text) ? "容大进销存用户" : customer.Text.Trim();
                string payloadText = "IVS1|" + installationId.ToString().ToUpperInvariant() + "|"
                    + expiryEpoch + "|" + Base64Url(Encoding.UTF8.GetBytes(customerText));
                byte[] payload = Encoding.UTF8.GetBytes(payloadText);

                using (var rsa = new RSACryptoServiceProvider())
                {
                    rsa.PersistKeyInCsp = false;
                    rsa.FromXmlString(File.ReadAllText(privateKeyPath, Encoding.UTF8));
                    byte[] signature = rsa.SignData(payload, "SHA256");
                    activationCode.Text = Base64Url(payload) + "." + Base64Url(signature);
                }
                status.Text = "注册码已生成，请复制后粘贴到客户软件。";
            }
            catch (Exception ex)
            {
                status.Text = "生成失败";
                MessageBox.Show(this, ex.Message, "注册器", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static string Base64Url(byte[] data)
        {
            return Convert.ToBase64String(data).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        }
    }
}
