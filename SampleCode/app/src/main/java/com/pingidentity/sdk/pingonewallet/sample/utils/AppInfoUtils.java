package com.pingidentity.sdk.pingonewallet.sample.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.pingidentity.did.sdk.client.service.model.ApplicationInstance;
import com.pingidentity.sdk.pingonewallet.types.regions.PingOneRegion;

import java.util.Map;

public class AppInfoUtils {

    public static final String START_POLLING = "start polling";
    public static final String STOP_POLLING = "stop polling";
    public static final String ENABLE_PUSH = "enable push";
    public static final String DISABLE_PUSH = "disable push";
    public static final String DELETE_CREDS = "delete creds";
    private static final String TAG = AppInfoUtils.class.getCanonicalName();

    public static String getAppInfoMessageFor(Map<PingOneRegion, ApplicationInstance> applicationInstances, Context context) {
        StringBuilder message = new StringBuilder();

        String version = "Not available";
        String buildNumber = null;

        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            version = packageInfo.versionName;
            buildNumber = String.valueOf(packageInfo.versionCode);
            message.append("Version: ").append(version);
            message.append(" (").append(buildNumber).append(")");
            message.append("\n--------------------------");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot get version details", e);
        }

        StringBuilder appInstanceMessage = new StringBuilder();
        String pushToken = null;

        for (Map.Entry<PingOneRegion, ApplicationInstance> entry : applicationInstances.entrySet()) {
            if (pushToken == null) {
                pushToken = entry.getValue().getPushToken();
            }
            appInstanceMessage.append("\n* URL: ").append(entry.getKey().getBaseUrl())
                    .append("\n* ID: ").append(entry.getValue().getId());
        }
        message.append(appInstanceMessage).append("\n--------------------------");

        if (pushToken != null) {
            message.append("\n* Push Token: ").append(pushToken);
        }

        return message.toString();
    }

}
