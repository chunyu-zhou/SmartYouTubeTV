package com.liskovsoft.smartyoutubetv.events;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.liskovsoft.browser.Controller;
import com.liskovsoft.browser.Tab;
import com.liskovsoft.smartyoutubetv.R;
import com.liskovsoft.smartyoutubetv.flavors.common.FragmentManagerActivity;
import com.liskovsoft.smartyoutubetv.fragments.TwoFragmentsManager;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.injectors.DecipherRoutineInjector;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.injectors.GenericEventResourceInjector;
import com.liskovsoft.smartyoutubetv.injectors.WebViewJavaScriptInterface;
import com.liskovsoft.smartyoutubetv.interceptors.MainRequestInterceptor;
import com.liskovsoft.smartyoutubetv.interceptors.RequestInterceptor;
import com.liskovsoft.smartyoutubetv.misc.ErrorTranslator;
import com.liskovsoft.smartyoutubetv.misc.KeysTranslator;
import com.liskovsoft.smartyoutubetv.misc.MainApkUpdater;
import com.liskovsoft.smartyoutubetv.misc.MyCookieSaver;
import com.liskovsoft.smartyoutubetv.misc.StateUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerEventListener implements Controller.EventListener, Tab.EventListener {
    private static final Logger logger = LoggerFactory.getLogger(ControllerEventListener.class);
    private static final String JS_INTERFACE_NAME = "app";
    private final Context mContext;
    private final KeysTranslator mTranslator;
    private final WebViewJavaScriptInterface mJSInterface;
    // private final VideoFormatInjector mFormatInjector;
    private final DecipherRoutineInjector mDecipherInjector;
    private final GenericEventResourceInjector mGenericInjector;
    private final StateUpdater mStateUpdater;
    private final MainApkUpdater mApkUpdater;
    private final Controller mController;
    private final RequestInterceptor mInterceptor;
    private final ErrorTranslator mErrorTranslator;

    public ControllerEventListener(Activity context, Controller controller, KeysTranslator translator) {
        mContext = context;
        mController = controller;
        mTranslator = translator;
        mStateUpdater = new StateUpdater(null, context);
        mApkUpdater = new MainApkUpdater(context);

        // mFormatInjector = new VideoFormatInjector(mContext);
        mDecipherInjector = new DecipherRoutineInjector(mContext);
        mGenericInjector = new GenericEventResourceInjector(mContext);
        mJSInterface = new WebViewJavaScriptInterface(mContext);
        mInterceptor = new MainRequestInterceptor(mContext);
        mErrorTranslator = new ErrorTranslator(mContext);
    }

    private FrameLayout getRootView() {
        return ((AppCompatActivity) mContext).getWindow().getDecorView().findViewById(android.R.id.content);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(Tab tab, WebResourceRequest request) {
        if (VERSION.SDK_INT >= 21) {
            String url = request.getUrl().toString();
            return processRequest(url);
        }

        return null;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(Tab tab, String url) {
        return processRequest(url);
    }

    private WebResourceResponse processRequest(String url) {
        if (mInterceptor.test(url)) {
            return mInterceptor.intercept(url);
        }

        return null;
    }

    private void syncCookies(Tab tab) {
        MyCookieSaver.saveCookie(tab.getWebView());
    }

    @Override
    public void onPageStarted(Tab tab, Bitmap favicon) {
        // js must be added before page fully loaded???
        // addJSInterface(tab);
    }

    /**
     * inject here custom styles and scripts
     */
    @Override
    public void onPageFinished(Tab tab, String url) {
        bindTabToInjectors(tab);
        syncCookies(tab);
    }

    /**
     * CAUTION: do all your error stuff here. Why, see below.
     * <br/>
     * In other places {@link WebView#getUrl WebView.getUrl} may return <code>null</code> because page not done loading.
     * <br/>
     * I've got a mistake. I tried to wait {@link #onPageFinished(Tab, String) onPageFinished} event. DO NOT DO THIS.
     * <br/>
     * <a href="https://stackoverflow.com/questions/13773037/webview-geturl-returns-null-because-page-not-done-loading">More info</a>
     * @param tab tab
     * @param errorCode see {@link android.webkit.WebViewClient} for details
     */
    @Override
    public void onReceiveError(Tab tab, int errorCode) {
        logger.info("onReceiveError called: errorCode: " + errorCode);
        if (mContext instanceof FragmentManagerActivity) {
            ((FragmentManagerActivity) mContext).getLoadingManager().setMessage(mErrorTranslator.translate(errorCode));
        }
        tab.reload();
    }

    @Override
    public void onLoadSuccess(Tab tab) {
        mTranslator.enable();
        mApkUpdater.start();

        if (mContext instanceof  TwoFragmentsManager) {
            ((TwoFragmentsManager) mContext).onBrowserReady();
        }
    }

    @Override
    public void onTabCreated(Tab tab) {
        addJSInterface(tab);
        tab.setListener(this);
        // mLoadingManager.show();
    }

    @Override
    public void onControllerStart() {
        // if you need to disable auto-save webview state:
        // mController.getCrashRecoveryHandler().pauseState();
    }

    @Override
    public void onSaveControllerState(Bundle state) {
    }

    @Override
    public void onRestoreControllerState(Bundle state) {
        mStateUpdater.updateState(state);
    }

    @SuppressLint("NewApi")
    private void addJSInterface(Tab tab) {
        logger.info("ControllerEventListener::on addJSInterface");

        mJSInterface.add(tab);

        WebView webView = tab.getWebView();
        webView.addJavascriptInterface(mJSInterface, JS_INTERFACE_NAME);
    }

    private void bindTabToInjectors(Tab tab) {
        WebView w = tab.getWebView();
        mDecipherInjector.add(w);
        mGenericInjector.add(w);
    }
}
