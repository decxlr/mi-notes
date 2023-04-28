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

package net.micode.notes.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;


/*
 *该类功能：NotesPreferenceActivity，在小米便签中主要实现的是对背景颜色和字体大小的数据储存。
 *       继承了PreferenceActivity主要功能为对系统信息和配置进行自动保存的Activity
 */
public class NotesPreferenceActivity extends PreferenceActivity {
    //优先名
    public static final String PREFERENCE_NAME = "notes_preferences";
    //同步账号
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";
    //同步时间
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";
    //同步密码
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";
    //本地密码
    private static final String AUTHORITIES_FILTER_KEY = "authorities";
    //账户分组
    private PreferenceCategory mAccountCategory;
    //同步任务接收器
    private GTaskReceiver mReceiver;
    //账户
    private Account[] mOriAccounts;
    //账户的hash标记
    private boolean mHasAddedAccount;

    @Override
    //创建一个activity,在函数里完场所有的正常静态设置
    //icicle存放了activity当前的状态
    protected void onCreate(Bundle icicle) {
        //先执行父类创建函数
        super.onCreate(icicle);

        /* using the app icon for navigation */
        getActionBar().setDisplayHomeAsUpEnabled(true);
        //添加xml来源并显示xml
        addPreferencesFromResource(R.xml.preferences);
        //根据同步账户关键码来初始化分组
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        mOriAccounts = null;
        //获取listview,用于列出所有选择
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        //在listview组件上方添加其他组件
        getListView().addHeaderView(header, null, true);
    }

    @Override
    //activity交互功能的实现,用于接收用户的输入
    protected void onResume() {
        //先执行父类交互实现
        super.onResume();

        // need to set sync account automatically if user has added a new
        // account
        //若用户新加了账户则自动设置同步账户
        if (mHasAddedAccount) {
            //获取Google同步账户
            Account[] accounts = getGoogleAccounts();
            //若原账户不为空且当前账户有增加
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        //更新账户
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    //若没有找到旧的账户,那么同步账号中就只添加新账户
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }
        //刷新标签界面
        refreshUI();
    }

    @Override
    //销毁一个activity
    protected void onDestroy() {
        //注销接收器
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        //执行父类销毁
        super.onDestroy();
    }

    //重新设置账户信息
    private void loadAccountPreference() {
        //销毁所有分组
        mAccountCategory.removeAll();

        //建立首选项
        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        
        //设置首选项的大标题和小标题
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));

        //建立监听器
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // the first time to set account
                        showSelectAccountAlertDialog();
                    } else {
                        // if the account has already been set, we need to promp
                        // user about the risk
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });
        //根据新建首选项编辑新的账户分组
        mAccountCategory.addPreference(accountPref);
    }

    //设置按键的状态和最后同步时间
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        //获取同步按钮控件和最终同步时间的的窗口
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // set button state
        if (GTaskSyncService.isSyncing()) {
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // set last sync time
        if (GTaskSyncService.isSyncing()) {
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    //刷新标签页面
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    //显示账户选择的对话框并进行账户的设置
    private void showSelectAccountAlertDialog() {
        //创建新的对话框
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));
        //设置标题以及子标题的内容
        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);
        //设置对话框的自定义标题，建立一个YES的按钮
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);
        //获取同步账户信息
        mOriAccounts = accounts;
        mHasAddedAccount = false;
 
        if (accounts.length > 0) {
        	//若账户不为空
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                    //在账户列表中查询到所需账户
                }
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
            		//在对话框建立一个单选的复选框
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            //取消对话框
                            refreshUI();
                        }
                        //设置点击后执行的事件，包括检录新同步账户和刷新标签界面
                    });
            //建立对话框网络版的监听器
        }
 
        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);
        //给新加账户对话框设置自定义样式
 
        final AlertDialog dialog = dialogBuilder.show();
        //显示对话框
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                //将新加账户的hash置true
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                //建立网络建立组件
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    "gmail-ls"
                });
                startActivityForResult(intent, -1);
                //跳回上一个选项
                dialog.dismiss();
            }
        });
        //建立新加账户对话框的监听器
    }
 
    /*
     * 函数功能：显示账户选择对话框和相关账户操作
     * 函数实现：如下注释
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        //创建一个新的对话框
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        //根据同步修改的账户信息设置标题以及子标题的内容
        dialogBuilder.setCustomTitle(titleView);
      //设置对话框的自定义标题
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        //定义一些标记字符串
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
        	//设置对话框要显示的一个list，用于显示几个命令时,即change，remove，cancel
            public void onClick(DialogInterface dialog, int which) {
            	//按键功能，由which来决定
                if (which == 0) {
                	//进入账户选择对话框
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                	//删除账户并且跟新便签界面
                    removeSyncAccount();
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
        //显示对话框
    }
 
    /*
     *函数功能：获取谷歌账户
     *函数实现：通过账户管理器直接获取
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }
 
    /*
     * 函数功能：设置同步账户
     * 函数实现：如下注释：
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
        	//假如该账号不在同步账号列表中
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            //编辑共享的首选项
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            //将该账号加入到首选项中
            
            editor.commit();
            //提交修改的数据
 
            
            setLastSyncTime(this, 0);
          //将最后同步时间清零
 
            // clean up local gtask related info
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");
                    values.put(NoteColumns.SYNC_ID, 0);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();
            //重置当地同步任务的信息
 
            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
            //将toast的文本信息置为“设置账户成功”并显示出来
        }
    }
    //函数功能：删除同步账户
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        //设置共享首选项
        
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
            //假如当前首选项中有账户就删除
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
            //删除当前首选项中有账户时间
        }
        editor.commit();
        //提交更新后的数据
        
        // clean up local gtask related info
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
      //重置当地同步任务的信息
    }
    
    //函数功能：获取同步账户名称
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }
 
    //函数功能：设置最终同步的时间
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        // 从共享首选项中找到相关账户并获取其编辑器
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
        //编辑最终同步时间并提交更新
    }

    //函数功能：获取最终同步时间
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }
 
    //函数功能：接受同步信息
    private class GTaskReceiver extends BroadcastReceiver {
 
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                //获取随广播而来的Intent中的同步服务的数据
            	TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
                //通过获取的数据在设置系统的状态
            }
 
        }
    }
 
    //函数功能：处理菜单的选项
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        //根据选项的id选择，这里只有一个主页
            case android.R.id.home:
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
                //在主页情况下在创建连接组件intent，发出清空的信号并开始一个相应的activity
            default:
                return false;
        }
    }
}
