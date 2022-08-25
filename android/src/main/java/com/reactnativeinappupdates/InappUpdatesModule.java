package com.reactnativeinappupdates;

import androidx.annotation.NonNull;

import android.app.Activity;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableNativeMap;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallState;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;

@ReactModule(name = InappUpdatesModule.NAME)
public class InappUpdatesModule extends ReactContextBaseJavaModule {
    public static final String NAME = "InappUpdates";

    private final ReactApplicationContext reactContext;
    private AppUpdateManager appUpdateManager;
    private boolean isDownloadSuccess = false;

    public InappUpdatesModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.appUpdateManager = AppUpdateManagerFactory.create(reactContext);
        this.appUpdateManager.registerListener(this::statusDownloadListener);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    public void statusDownloadListener(InstallState state) {
        this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("installStatus", state.installStatus());
        if (state.installStatus() == InstallStatus.DOWNLOADING) {
            long percentageDownloaded = state.bytesDownloaded() * 100 / state.totalBytesToDownload();
            WritableMap downloadInfo = new WritableNativeMap();
            downloadInfo.putString("bytesDownloaded", String.valueOf(state.bytesDownloaded()));
            downloadInfo.putString("totalBytesDownloaded", String.valueOf(state.totalBytesToDownload()));
            downloadInfo.putString("percentageDownloaded", String.valueOf(percentageDownloaded));
            this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("downloadInfo", downloadInfo);
        }
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            this.isDownloadSuccess = true;
        }
    }

    @Override
    public void onCatalystInstanceDestroy() {
        this.appUpdateManager.unregisterListener(this::statusDownloadListener);
    }

    protected void checkUpdate(final Promise promise, int appUpdateType, int clientVersionStalenessDays) {
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && (appUpdateInfo.clientVersionStalenessDays() == null ? clientVersionStalenessDays == 0 : appUpdateInfo.clientVersionStalenessDays() >= clientVersionStalenessDays)
                    && appUpdateInfo.isUpdateTypeAllowed(appUpdateType)) {
                AppUpdateOptions options = AppUpdateOptions.newBuilder(appUpdateType).build();
                final Activity activity = getCurrentActivity();
                Task<Integer> startUpdateFlow = appUpdateManager.startUpdateFlow(appUpdateInfo, activity, options);

                startUpdateFlow.addOnFailureListener(failure -> {
                    promise.reject("reject", "startUpdateFlow failure" + failure.toString());
                });

                startUpdateFlow.addOnSuccessListener(result -> {
                    promise.resolve(result == 0 ? "Canceled" : "Successful");
                });
            } else if(appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                promise.resolve("Update Already Downloaded");
            } 
            else {
                promise.reject("reject", "No update available");
            }
        });

        appUpdateInfoTask.addOnFailureListener(failure -> {
            promise.reject("reject", "checkAppUpdate failure: " + failure.toString());
        });
    }

    @ReactMethod
    public void checkAppUpdate(int appUpdateType, int clientVersionStalenessDays, final Promise promise) {
        checkUpdate(promise, appUpdateType, clientVersionStalenessDays);
    }

    @ReactMethod
    public void checkUpdateStatus(final Promise promise) {
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                promise.resolve("Update available");
            } else {
                promise.reject("reject", "No update available");
            }
        }).addOnFailureListener(failure -> {
            promise.reject("reject", "checkUpdateStatus failure: " + failure.toString());
        });
    }

    @ReactMethod
    public void completeUpdate(final Promise promise) {
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if(appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                this.appUpdateManager.completeUpdate();
                promise.resolve("success");
            } 
            else {
                promise.reject("reject", "Download is not completed");
            }
        });
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Keep: Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Keep: Required for RN built in Event Emitter Calls.
    }
}
