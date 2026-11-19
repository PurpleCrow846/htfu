// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

/**
 * 2.09: keeps the WebView on the app page. Any http(s), mailto or tel link opens outside the app
 * (ACTION_VIEW). The app has no INTERNET permission, so a web page inside the WebView is always a
 * dead end ("net::ERR_CACHE_MISS").
 */
public final class ExternalLinks {
    public static final String APP_URL = "file:///android_asset/www/index.html";

    private ExternalLinks() {}

    /** True for the app's own page (any query or hash). */
    public static boolean isAppPage(String url) {
        return url != null && (url.startsWith(APP_URL) || url.startsWith("about:blank"));
    }

    /** Opens url outside the app if it is an external link. Returns true when the WebView must not load it. */
    public static boolean intercept(final Activity activity, String url) {
        if (url == null || isAppPage(url)) return false;
        if (url.startsWith("javascript:") || url.startsWith("data:") || url.startsWith("blob:")) return false;
        open(activity, url);
        return true;
    }

    public static void open(final Activity activity, final String url) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Uri uri = Uri.parse(url);
                String s = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                if (!s.equals("http") && !s.equals("https") && !s.equals("mailto") && !s.equals("tel")) {
                    Toast.makeText(activity, "Can't open that link.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    activity.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(activity, "No browser found to open the link.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(activity, "Can't open that link.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /** If the WebView ever ends up on another page, put the app page back. */
    public static void restoreAppPage(WebView view) {
        if (view == null) return;
        String cur = view.getUrl();
        if (cur != null && !isAppPage(cur)) {
            view.stopLoading();
            view.loadUrl(APP_URL);
        }
    }

    /** JS bridge: window.AndroidLinks.open(url) */
    public static final class Bridge {
        private final Activity activity;

        public Bridge(Activity activity) { this.activity = activity; }

        @JavascriptInterface
        public void open(String url) { ExternalLinks.open(activity, url); }

        @JavascriptInterface
        public boolean available() { return true; }
    }
}
