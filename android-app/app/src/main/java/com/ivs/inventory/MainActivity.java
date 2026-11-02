package com.ivs.inventory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "ivs_preferences";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String UPDATE_PAGE =
            "https://github.com/wang66676/ivs-inventory-system/releases/latest";
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(6), dp(8), dp(6));
        toolbar.setBackgroundColor(0xFF1677FF);

        TextView title = new TextView(this);
        title.setText("IVS 进销存  v" + BuildConfig.VERSION_NAME);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        toolbar.addView(toolbarButton("服务器", view -> showServerSettings()));
        toolbar.addView(toolbarButton("更新", view -> openUpdatePage()));
        toolbar.addView(toolbarButton("刷新", view -> webView.reload()));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(settings.getUserAgentString() + " IVS-Android/" + BuildConfig.VERSION_NAME);

        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new IvsWebViewClient());
        webView.setWebChromeClient(new IvsWebChromeClient());
        webView.setDownloadListener(new IvsDownloadListener());
        webView.loadUrl(serverUrl());
    }

    private Button toolbarButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(0xFFFFFFFF);
        button.setAllCaps(false);
        button.setBackgroundColor(0x00000000);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(listener);
        return button;
    }

    private String serverUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_SERVER_URL, BuildConfig.DEFAULT_SERVER_URL);
        if (saved == null || saved.trim().isEmpty()) saved = BuildConfig.DEFAULT_SERVER_URL;
        return saved.endsWith("/") ? saved : saved + "/";
    }

    private void showServerSettings() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(serverUrl());
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("服务器地址")
                .setMessage("填写网页登录版地址，例如 https://example.com/")
                .setView(input)
                .setPositiveButton("保存并打开", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.startsWith("http://") && !value.startsWith("https://")) {
                        Toast.makeText(this, "地址必须以 http:// 或 https:// 开头", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!value.endsWith("/")) value += "/";
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SERVER_URL, value).apply();
                    webView.loadUrl(value);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openUpdatePage() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_PAGE)));
    }

    private boolean isInternalUrl(Uri uri) {
        Uri server = Uri.parse(serverUrl());
        return uri.getHost() != null && uri.getHost().equalsIgnoreCase(server.getHost());
    }

    private final class IvsWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isInternalUrl(uri)) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
                Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }

    private final class IvsWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (Exception exception) {
                fileCallback = null;
                return false;
            }
        }
    }

    private final class IvsDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimeType, long contentLength) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) request.addRequestHeader("Cookie", cookie);
                String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(fileName);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                manager.enqueue(request);
                Toast.makeText(MainActivity.this, "文件已开始下载", Toast.LENGTH_SHORT).show();
            } catch (Exception exception) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            result = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
