package coji.ro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private boolean keepScreenAwake;
    private boolean activityResumed;
    private boolean tvDevice;
    private boolean mediaPlaying;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tvDevice = isTvDevice();

        if (tvDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (tvDevice) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        webView = new WebView(this);
        webView.setKeepScreenOn(false);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        webView.addJavascriptInterface(new MediaPlaybackBridge(), "CojiAndroid");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(tvDevice);
        settings.setUseWideViewPort(!tvDevice);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        settings.setBuiltInZoomControls(tvDevice);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectMediaPlaybackObserver();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        if (tvDevice) {
            webView.setInitialScale(160);
        }

        setContentView(webView);
        webView.loadUrl("https://coji.ro");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleMediaIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;

        if (webView != null) {
            setScreenAwake(false);

            if (tvDevice) {
                webView.evaluateJavascript(
                        "document.querySelectorAll('audio, video').forEach(el => { el.pause(); el.currentTime = 0; });",
                        null
                );
                webView.onPause();
            }
            // On mobile: do NOT pause webView - let audio continue in background
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;

        if (webView != null) {
            webView.onResume();
            injectMediaPlaybackObserver();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            if (webView != null) {
                setScreenAwake(false);
                webView.evaluateJavascript(
                        "document.querySelectorAll('audio, video').forEach(el => { el.pause(); el.currentTime = 0; });",
                        null
                );
            }
            stopMediaService();
            super.onBackPressed();
        }
    }

    private void handleMediaIntent(Intent intent) {
        if (intent == null || webView == null) return;
        String action = intent.getAction();
        if ("coji.ro.action.MEDIA_PLAY".equals(action)) {
            webView.evaluateJavascript(
                    "document.querySelectorAll('audio, video').forEach(el => { if(el.paused) el.play(); });",
                    null
            );
        } else if ("coji.ro.action.MEDIA_PAUSE".equals(action)) {
            webView.evaluateJavascript(
                    "document.querySelectorAll('audio, video').forEach(el => { if(!el.paused) el.pause(); });",
                    null
            );
        }
    }

    private void setScreenAwake(boolean awake) {
        if (keepScreenAwake == awake) {
            return;
        }

        keepScreenAwake = awake;

        if (awake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (webView != null) {
            webView.setKeepScreenOn(awake);
        }
    }

    private void startMediaService() {
        if (tvDevice) return;
        Intent serviceIntent = new Intent(this, MediaPlaybackService.class);
        serviceIntent.setAction(MediaPlaybackService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopMediaService() {
        if (tvDevice) return;
        Intent serviceIntent = new Intent(this, MediaPlaybackService.class);
        serviceIntent.setAction(MediaPlaybackService.ACTION_STOP);
        startService(serviceIntent);
    }

    private void updateMediaMetadata(String title, String artist) {
        if (tvDevice) return;
        Intent serviceIntent = new Intent(this, MediaPlaybackService.class);
        serviceIntent.setAction(MediaPlaybackService.ACTION_UPDATE_METADATA);
        serviceIntent.putExtra(MediaPlaybackService.EXTRA_TITLE, title);
        serviceIntent.putExtra(MediaPlaybackService.EXTRA_ARTIST, artist);
        startService(serviceIntent);
    }

    private void injectMediaPlaybackObserver() {
        if (webView == null) {
            return;
        }

        webView.evaluateJavascript(
                "(function() {"
                        + "if (window.__cojiPlaybackObserverInstalled) {"
                        + "  window.__cojiReportPlayback && window.__cojiReportPlayback();"
                        + "  return;"
                        + "}"
                        + "window.__cojiPlaybackObserverInstalled = true;"
                        + "window.__cojiReportPlayback = function() {"
                        + "  var playing = Array.prototype.some.call(document.querySelectorAll('audio, video'), function(el) {"
                        + "    return !el.paused && !el.ended;"
                        + "  });"
                        + "  var title = 'Radio Coji';"
                        + "  var artist = 'Live';"
                        + "  if (navigator.mediaSession && navigator.mediaSession.metadata) {"
                        + "    title = navigator.mediaSession.metadata.title || title;"
                        + "    artist = navigator.mediaSession.metadata.artist || artist;"
                        + "  }"
                        + "  if (window.CojiAndroid && window.CojiAndroid.setMediaPlaying) {"
                        + "    window.CojiAndroid.setMediaPlaying(playing, title, artist);"
                        + "  }"
                        + "};"
                        + "function bind(el) {"
                        + "  if (el.__cojiPlaybackBound) return;"
                        + "  el.__cojiPlaybackBound = true;"
                        + "  ['play','playing','pause','ended','emptied','stalled','suspend','waiting'].forEach(function(eventName) {"
                        + "    el.addEventListener(eventName, window.__cojiReportPlayback, true);"
                        + "  });"
                        + "}"
                        + "function bindAll() {"
                        + "  document.querySelectorAll('audio, video').forEach(bind);"
                        + "  window.__cojiReportPlayback();"
                        + "}"
                        + "new MutationObserver(bindAll).observe(document.documentElement, { childList: true, subtree: true });"
                        + "bindAll();"
                        + "setInterval(window.__cojiReportPlayback, 5000);"
                        + "})();",
                null
        );
    }

    private boolean isTvDevice() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean tvMode = uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        boolean hasLeanback = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);

        return tvMode || hasLeanback;
    }

    private class MediaPlaybackBridge {
        @JavascriptInterface
        public void setMediaPlaying(boolean playing, String title, String artist) {
            runOnUiThread(() -> {
                mediaPlaying = playing;
                if (activityResumed) {
                    setScreenAwake(playing);
                }
                if (!tvDevice) {
                    if (playing) {
                        updateMediaMetadata(title, artist);
                        startMediaService();
                    } else {
                        stopMediaService();
                    }
                }
            });
        }
    }
}
