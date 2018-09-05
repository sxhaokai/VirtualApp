package com.yunke.service.virtual;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.os.VUserInfo;
import com.lody.virtual.os.VUserManager;
import com.lody.virtual.remote.InstallResult;
import com.lody.virtual.remote.InstalledAppInfo;
import com.yunke.service.virtual.models.AppData;
import com.yunke.service.virtual.models.AppInfo;
import com.yunke.service.virtual.models.AppInfoLite;
import com.yunke.service.virtual.models.MultiplePackageAppData;
import com.yunke.service.virtual.models.PackageAppData;
import com.yunke.service.virtual.repo.AppRepository;
import com.yunke.service.virtual.repo.PackageAppDataStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Created by haokai on 2018/9/5.
 */

public class WechatMonitor {


    //    微信包名
    private static final String WE_CHAT_PACKAGE_NAME = "com.tencent.mm";

    public static AppInfo getWechatInfo(Context context) {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> installedPackages = pm.getInstalledPackages(0);
        if (installedPackages != null) {
            for (PackageInfo installedPackage : installedPackages) {
                if (WE_CHAT_PACKAGE_NAME.equals(installedPackage.packageName)) {
                    ApplicationInfo ai = installedPackage.applicationInfo;
                    String path = ai.publicSourceDir != null ? ai.publicSourceDir : ai.sourceDir;
                    if (path == null) {
                        return null;
                    }
                    AppInfo info = new AppInfo();
                    info.packageName = installedPackage.packageName;
                    // TODO: 2018/9/5  true  还是 false?
                    info.fastOpen = true;
                    info.path = path;
                    info.icon = ai.loadIcon(pm);
                    info.name = ai.loadLabel(pm);
                    InstalledAppInfo installedAppInfo = VirtualCore.get().getInstalledAppInfo(installedPackage.packageName, 0);
                    if (installedAppInfo != null) {
                        //克隆的数量
                        info.cloneCount = installedAppInfo.getInstalledUsers().length;
                    }
                    return info;
                }
            }
        }
        //没有安装微信
        return null;
    }

    public static void cloneApp(Context context,AppInfo appinfo) {
        AppInfoLite info =  new AppInfoLite(appinfo.packageName, appinfo.path, appinfo.fastOpen);

        AppRepository mRepo = new AppRepository(context);

        class AddResult {
            private PackageAppData appData;
            private int userId;
            private boolean justEnableHidden;
        }
        AddResult addResult = new AddResult();
        VUiKit.defer().when(() -> {
            InstalledAppInfo installedAppInfo = VirtualCore.get().getInstalledAppInfo(info.packageName, 0);
            addResult.justEnableHidden = installedAppInfo != null;
            if (addResult.justEnableHidden) {
                int[] userIds = installedAppInfo.getInstalledUsers();
                int nextUserId = userIds.length;
                /*
                  Input : userIds = {0, 1, 3}
                  Output: nextUserId = 2
                 */
                for (int i = 0; i < userIds.length; i++) {
                    if (userIds[i] != i) {
                        nextUserId = i;
                        break;
                    }
                }
                addResult.userId = nextUserId;

                if (VUserManager.get().getUserInfo(nextUserId) == null) {
                    // user not exist, create it automatically.
                    String nextUserName = "Space " + (nextUserId + 1);
                    VUserInfo newUserInfo = VUserManager.get().createUser(nextUserName, VUserInfo.FLAG_ADMIN);
                    if (newUserInfo == null) {
                        throw new IllegalStateException();
                    }
                }
                boolean success = VirtualCore.get().installPackageAsUser(nextUserId, info.packageName);
                if (!success) {
                    throw new IllegalStateException();
                }
            } else {
                InstallResult res = mRepo.addVirtualApp(info);
                if (!res.isSuccess) {
                    throw new IllegalStateException();
                }
            }
        }).then((res) -> {
            addResult.appData = PackageAppDataStorage.get().acquire(info.packageName);
        }).done(res -> {
            boolean multipleVersion = addResult.justEnableHidden && addResult.userId != 0;
            if (!multipleVersion) {
                PackageAppData data = addResult.appData;
                data.isLoading = true;
//                    mView.addAppToLauncher(data);
//                    handleOptApp(data, info.packageName, true);
            } else {
                MultiplePackageAppData data = new MultiplePackageAppData(addResult.appData, addResult.userId);
                data.isLoading = true;
//                    mView.addAppToLauncher(data);
//                    handleOptApp(data, info.packageName, false);
            }
        });
    }

    public static void launchApp(Activity a) {
        AppData data = getCloneWechatInfo(a);
        try {
            if (data instanceof PackageAppData) {
                PackageAppData appData = (PackageAppData) data;
                appData.isFirstOpen = false;
                LoadingActivity.launch(a, appData.packageName, 0);
            } else if (data instanceof MultiplePackageAppData) {
                MultiplePackageAppData multipleData = (MultiplePackageAppData) data;
                multipleData.isFirstOpen = false;
                LoadingActivity.launch(a, multipleData.appInfo.packageName, ((MultiplePackageAppData) data).userId);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static AppData getCloneWechatInfo(Context context) {
        List<InstalledAppInfo> infos = VirtualCore.get().getInstalledApps(0);
        for (InstalledAppInfo info : infos) {
            if (WE_CHAT_PACKAGE_NAME.equals(info.packageName)) {
                if (!VirtualCore.get().isPackageLaunchable(info.packageName)) {
                    Log.e("VirtualApp", "isPackageLaunchable ： " + false);
                    return null;
                }
                PackageAppData data = new PackageAppData(context, info);
                if (VirtualCore.get().isAppInstalledAsUser(0, info.packageName)) {
                    return data;
                }
                int[] userIds = info.getInstalledUsers();
                for (int userId : userIds) {
                    if (userId != 0) {
                        return new MultiplePackageAppData(data, userId);
                    }
                }

            }
        }
        return null;
    }
}
