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
/* 该类实现正常桌面的挂件*/
package net.micode.notes.widget;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

public abstract class NoteWidgetProvider extends AppWidgetProvider {
    /*
    extends用法继承或者覆盖
    继承——派生类继承了父类的所有方法。
    覆盖——在子类中定义一个与父类同名，返回类型，参数类型均相同的一个方法
    abstract class 定义抽象类，如果设计这个类的某个描述方法不清楚，则应该定义为抽象类，可以在以后继承这个抽象类的时候进行完善
    */
    public static final String [] PROJECTION = new String [] {  /*定义了一个字符数组类型的静态变量*/
        NoteColumns.ID,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.SNIPPET
    };

    public static final int COLUMN_ID           = 0;           /*便签栏编号*/
    public static final int COLUMN_BG_COLOR_ID  = 1;
    public static final int COLUMN_SNIPPET      = 2;

    private static final String TAG = "NoteWidgetProvider";     /*定义NoteWidgetProvider为标签TAG*/

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        /*
        方法：重载删除的过程，在删除的同时，把当前对应的窗口给关闭，也即把WIDGET_ID映射
        为INVALID_APPWIGET_ID，就是把当前窗口id标记为无效窗口。然后把这个修改应用到所有关联的uri便签
        */
        ContentValues values = new ContentValues();                /*对每个AppWidget，可以创建其多个实例，这些实例对应于不同的appWidgetId*/
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);   /*对每个AppWidget，可以创建其多个实例，这些实例对应于不同的appWidgetId*/
        for (int i = 0; i < appWidgetIds.length; i++) {             /*遍历修改所有的URI值*/
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i])});
        }
    }

    private Cursor getNoteWidgetInfo(Context context, int widgetId) {       /*获取窗口宽度信息*/
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {     /*上传widget的信息*/
        update(context, appWidgetManager, appWidgetIds, false);
    }

    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,         /*更新widget的信息方法*/
            boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {                                                 /*appWidgetId是每添加一个Widget会有一个WidgetId*/
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int bgId = ResourceParser.getDefaultBgId(context);
                String snippet = "";
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    intent.setAction(Intent.ACTION_VIEW);
                } else {
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                if (c != null) {
                    c.close();
                }

                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);
                /**
                 * Generate the pending intent to start host for the widget
                 */
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }
	

                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    protected abstract int getBgResourceId(int bgId);       /*从背景资源中获取当前应用ID*/

    protected abstract int getLayoutId();                   /*获取部局ID*/

    protected abstract int getWidgetType();                 /*可以调用2*2或者4*4的函数*/
}
