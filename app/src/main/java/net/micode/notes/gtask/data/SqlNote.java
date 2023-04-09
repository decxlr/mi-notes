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

package net.micode.notes.gtask.data;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.tool.ResourceParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 用于支持小米便签最底层的数据库相关操作
 */
public class SqlNote {
    /**
     * 调用getSimpleName()方法将类的简写名称存到字符串TAG中
     */
    private static final String TAG = SqlNote.class.getSimpleName();

    /**
     * 定义常量并赋初值-99999
     */
    private static final int INVALID_ID = -99999;

    /**
     * 集合了interface NoteColumns中所有17个SF常量
     */
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID, NoteColumns.ALERTED_DATE, NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE, NoteColumns.HAS_ATTACHMENT, NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT, NoteColumns.PARENT_ID, NoteColumns.SNIPPET, NoteColumns.TYPE,
            NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE, NoteColumns.SYNC_ID,
            NoteColumns.LOCAL_MODIFIED, NoteColumns.ORIGIN_PARENT_ID, NoteColumns.GTASK_ID,
            NoteColumns.VERSION
    };

    /**
     * 设置17个列的编号
     */
    public static final int ID_COLUMN = 0;

    public static final int ALERTED_DATE_COLUMN = 1;

    public static final int BG_COLOR_ID_COLUMN = 2;

    public static final int CREATED_DATE_COLUMN = 3;

    public static final int HAS_ATTACHMENT_COLUMN = 4;

    public static final int MODIFIED_DATE_COLUMN = 5;

    public static final int NOTES_COUNT_COLUMN = 6;

    public static final int PARENT_ID_COLUMN = 7;

    public static final int SNIPPET_COLUMN = 8;

    public static final int TYPE_COLUMN = 9;

    public static final int WIDGET_ID_COLUMN = 10;

    public static final int WIDGET_TYPE_COLUMN = 11;

    public static final int SYNC_ID_COLUMN = 12;

    public static final int LOCAL_MODIFIED_COLUMN = 13;

    public static final int ORIGIN_PARENT_ID_COLUMN = 14;

    public static final int GTASK_ID_COLUMN = 15;

    public static final int VERSION_COLUMN = 16;

    /**
     * 定义对应上述17个内部变量，帮助构造SqlNote
     */
    private Context mContext;

    private ContentResolver mContentResolver;

    private boolean mIsCreate;

    private long mId;

    private long mAlertDate;

    private int mBgColorId;

    private long mCreatedDate;

    private int mHasAttachment;

    private long mModifiedDate;

    private long mParentId;

    private String mSnippet;

    private int mType;

    private int mWidgetId;

    private int mWidgetType;

    private long mOriginParent;

    private long mVersion;

    private ContentValues mDiffNoteValues;

    private ArrayList<SqlData> mDataList;

    /**
     * 构造方法，参数只有context，初始化新建的对象中的所有变量
     * @param context Context
     */
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mId = INVALID_ID;
        mAlertDate = 0;
        mBgColorId = ResourceParser.getDefaultBgId(context);
        mCreatedDate = System.currentTimeMillis();
        mHasAttachment = 0;
        mModifiedDate = System.currentTimeMillis();
        mParentId = 0;
        mSnippet = "";
        mType = Notes.TYPE_NOTE;
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mOriginParent = 0;
        mVersion = 0;
        mDiffNoteValues = new ContentValues();
        mDataList = new ArrayList<SqlData>();
    }

    /**
     * 构造方法，参数有context和cursor，对cursor指向的对象进行初始化
     * @param context Context
     * @param c Cursor
     */
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE) {
            loadDataContent();
        }
        mDiffNoteValues = new ContentValues();
    }

    /**
     * 构造方法，参数有 context 和 id，对 id 指向的对象进行初始化
     * @param context Context
     * @param id long
     */
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(id);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE) {
            loadDataContent();
        }
        mDiffNoteValues = new ContentValues();

    }

    /**
     * 通过id从光标处加载数据
     * @param id long
     */
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            /*通过id获得对应的ContentResolver中的cursor*/
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(id)
                    }, null);
            if (c != null) {
                c.moveToNext();
                loadFromCursor(c);
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * 功能：通过cursor从光标加载数据
     * 实现：各种get类型的函数，通过光标c获取参数
     * @param c Cursor
     */
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);
        mParentId = c.getLong(PARENT_ID_COLUMN);
        mSnippet = c.getString(SNIPPET_COLUMN);
        mType = c.getInt(TYPE_COLUMN);
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);
        mVersion = c.getLong(VERSION_COLUMN);
    }

    /**
     * 通过content机制获取共享数据并加载到数据库当前游标处
     */
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();
        try {
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] {
                        String.valueOf(mId)
                    }, null);
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data");
                    return;
                }
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);
                    mDataList.add(data);
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null");
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * 设置通过content机制用于共享的数据信息
     * @param js JSONObject
     * @return boolean
     */
    public boolean setContent(JSONObject js) {
        try {
            /*创建一个JSONObject对象note*/
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            /*判断是不是系统文件夹*/
            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                /*警告，不能设置系统文件夹*/
                Log.w(TAG, "cannot set system folder");
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                /*对非系统文件只能更新或修改摘要snippet*/
                // for folder we can only update the snnipet and type
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                /*如果不是文件夹而是note，则进入*/
                /*获取便签提示日期*/
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                /*获取数据的ID*/
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                /*将该ID覆盖原ID*/
                mId = id;

                /*获取数据的提醒日期*/
                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                /*获取数据的背景颜色*/
                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                /*如果只是通过上下文对note进行数据库操作，或者该背景颜色与原背景颜色不相同*/
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                /*获取数据的创建日期*/
                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                /*如果只是通过上下文对note进行数据库操作，或者该创建日期与原创建日期不相同*/
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                /*获取数据的有无附件的布尔值,对其操作*/
                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                /*获取数据的修改日期*/
                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                /*如果只是通过上下文对note进行数据库操作，或者该修改日期与原修改日期不相同*/
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                /*获取数据的父ID*/
                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                /*如果只是通过上下文对note进行数据库操作，或者该父节点ID与原父节点ID不相同*/
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                /*将该父节点ID覆盖原父节点ID*/
                mParentId = parentId;

                /*获取数据的文本片段*/
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                /*获取数据的文件类型*/
                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                /*获取数据的小部件ID*/
                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                /*获取数据的小部件种类*/
                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                /*获取数据的原始父文件夹ID*/
                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                /*遍历 dataArray，查找 id 为 dataId 的数据*/
                for (int i = 0; i < dataArray.length(); i++) {
                    /* 依次获取数据表中的数据ID*/
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;
                    /*如果该数据ID对应的数据存在，将对应的数据存在数据库中*/
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                            }
                        }
                    }

                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }

                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 获取content机制提供的数据并加载到note中
     * @return JSONObject
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();

            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            JSONObject note = new JSONObject();
            /*如果对象的类型是note类型*/
            if (mType == Notes.TYPE_NOTE) {
                /*这个对象的13项按键值对方式加入note中*/
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                JSONArray dataArray = new JSONArray();
                /*将note中的data全部存入JSONArray中*/
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                /*类型为系统文件或目录文件时*/
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 给当前id设置父id
     * @param id long
     */
    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    /**
     * 给当前id设置Gtaskid
     * @param gid String
     */
    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    /**
     * 初始化本地修改，即撤销所有当前修改
     */
    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    /**
     * 获得当前id
     * @return long
     */
    public long getId() {
        return mId;
    }

    public long getParentId() {
        return mParentId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    /**
     * 判断是否为便签类型
     * @return boolean
     */
    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    /**
     * commit函数用于把当前造作所做的修改保存到数据库
     * @param validateVersion boolean
     */
    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            /*如果是一个无效的id并且还含有这个id，就将它移除*/
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            /*内容解析器中插入该便签的uri*/
            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            /*对于note类型，引用sqlData.commit方法操作*/
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        } else {
            /*如果该便签ID是无效ID，报错：没有这个便签*/
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }
            if (mDiffNoteValues.size() > 0) {
                mVersion ++;
                int result = 0;
                /*如果是无效版本*/
                if (!validateVersion) {
                    /*更新内容解析器：存入便签内容uri，便签ID，便签版本，mID，mVersion*/
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] {
                        String.valueOf(mId)
                    });
                } else {
                    /*如果是有效版本*/
                    /*更新内容解析器：存入便签内容uri，便签ID，便签版本，mID，mVersion*/
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] {
                                    String.valueOf(mId), String.valueOf(mVersion)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            /*对note类型，还是对其中的data引用commit，从而实现更改*/
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // refresh local info
        /*通过 cursor 从当前 id 处加载数据*/
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE) {
            loadDataContent();
        }

        /*清空，回到初始化状态*/
        mDiffNoteValues.clear();
        /*重置*/
        mIsCreate = false;
    }
}
