/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Service是在一段不定的时间运行在后台，不和用户交互的应用组件
 * GTaskSyncService继承自Service
 */
public class GTaskSyncService extends Service {
    /**
     * 定义了一系列静态变量，用来表示同步操作的状态。
     * 同步行为类型
     */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /**
     * 用数字0、1、2分别表示开始同步、取消同步、同步无效
     */
    public final static int ACTION_START_SYNC = 0;

    public final static int ACTION_CANCEL_SYNC = 1;

    public final static int ACTION_INVALID = 2;

    /**
     * 服务广播的名称
     */
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    /**
     * 正在同步中
     */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /**
     * 进程消息
     */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    private static GTaskASyncTask mSyncTask = null;

    private static String mSyncProgress = "";

    /**
     * 启动一个同步工作
     */
    private void startSync() {
        // 若当前没有同步工作，申请一个task并把指针指向新任务，广播后执行
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                @Override
                public void onComplete() {
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();
                }
            });
            sendBroadcast("");
            mSyncTask.execute();
        }
    }

    /**
     * 取消同步
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 初始化
     */
    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    /**
     * onStartCommand会告诉系统如何重启服务，如判断是否异常终止后重新启动，
     * 在何种情况下异常终止。返回值是一个(int)整形，有四种返回值。参数的flags表示启动服务的方式
     * @param intent Intent
     * @param flags int
     * @param startId int
     * @return int
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        // 判断当前的同步状态，根据开始或取消，执行对应操作
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            // 判断当前的同步状态，根据开始或取消，执行对应操作
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 发送广播
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 用于绑定操作的函数
     * @param intent Intent
     * @return IBinder
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 发送广播内容
     * @param msg String
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    /**
     * 开始同步
     * @param activity Activity
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 取消同步
     * @param context Context
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 判断当前是否处于同步状态
     * @return boolean
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 返回当前同步状态
     * @return String
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}
