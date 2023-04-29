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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;


/**
 * GTask管理类，封装了对GTask进行管理的一些方法
 */
public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();

    /**
     * 用0、1、2、3、4分别表示成功、网络错误、内部错误、同步中、取消同步
     */
    public static final int STATE_SUCCESS = 0;

    public static final int STATE_NETWORK_ERROR = 1;

    public static final int STATE_INTERNAL_ERROR = 2;

    public static final int STATE_SYNC_IN_PROGRESS = 3;

    public static final int STATE_SYNC_CANCELLED = 4;

    private static GTaskManager mInstance = null;

    /**
     * 活动
     */
    private Activity mActivity;

    private Context mContext;

    private ContentResolver mContentResolver;

    private boolean mSyncing;

    private boolean mCancelled;

    private HashMap<String, TaskList> mGTaskListHashMap;

    private HashMap<String, Node> mGTaskHashMap;

    private HashMap<String, MetaData> mMetaHashMap;

    private TaskList mMetaList;

    private HashSet<Long> mLocalDeleteIdMap;

    private HashMap<String, Long> mGidToNid;

    private HashMap<Long, String> mNidToGid;

    /**
     * 无参构造方法
     */
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    /**
     * 使用单例模式创建类的实例，所谓单例模式就是一个类有且只有一个实例
     * @return synchronized
     */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /**
     * 获取当前的操作并更新至GTask中
     * @param activity Activity
     */
    public synchronized void setActivityContext(Activity activity) {
        // used for getting authtoken
        mActivity = activity;
    }

    /**
     * 同步的总控制，包括同步前设置环境，进行同步，处理异常，同步结束清空缓存
     * @param context Context
     * @param asyncTask GTaskASyncTask
     * @return int
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 正在同步时，日志中写入正在同步
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        // 对GTaskManager的参数进行设置
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            // 实例化一个GTask用户对象
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // login google task
            // 若此时未取消同步操作，进行登录操作，尝试登录到google task
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // get the task list from google
            // 从谷歌获取任务列表
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            // 初始化GTaskList
            initGTaskList();

            // do content sync work
            // 进行同步操作
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 在同步操作结束之后，更新GTaskManager的属性
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 初始化GTask列表，将google上的JSONTaskList转为本地任务列表
     * @throws NetworkFailureException NetworkFailureException
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }
        // 实例化一个GTask用户对象
        GTaskClient client = GTaskClient.getInstance();
        try {
            JSONArray jsTaskLists = client.getTaskLists();

            // init meta list first
            mMetaList = null;
            // 对获取到任务列表中的每一个元素进行操作
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 如果name等于字符串"[MIUI_Notes]"+"METADATA"
                if (name
                        .equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // load meta data
                    // 获取元数据
                    JSONArray jsMetas = client.getTaskList(gid);
                    // 把jsMetas里的每一个有识别码的metaData都放到哈希表中
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // create meta list if not existed
            // 若元数据列表不存在则创建一个
            if (mMetaList == null) {
                // 创建一个新的任务列表
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // init task list
            // 初始化任务列表
            for (int i = 0; i < jsTaskLists.length(); i++) {
                // 获取列表中每一个节点的属性
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    // 对任务列表的内容进行设置
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // load tasks
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        // 获取当前任务的gid
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        // 设置任务内容
                        task.setContentByRemoteJSON(object);
                        // 判断该任务有无价值保存
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 实现内容同步的操作
     * @throws NetworkFailureException NetworkFailureException
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        // 初始化本地删除列表
        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // for local deleted note
        try {
            // 定位要删除的节点位置
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            // 若获取到的待删除便签不为空，则进行同步操作
            if (c != null) {
                // 通过while用指针遍历所有结点
                while (c.moveToNext()) {
                    // 获取待删除便签的gid
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    // 获取待删除便签的节点
                    node = mGTaskHashMap.get(gid);
                    // 节点非空则从哈希表里删除，并进行同步操作
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }

                    // 在本地删除记录中添加这一项记录
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // sync folder first
        // 先对文件夹进行同步
        syncFolder();

        // for note existing in database
        // 对已经存在与数据库的节点进行同步
        try {
            // 使c指针指向待操作的便签位置
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    // 获取待操作的便签的gid
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    // 获取待操作的便签的节点
                    node = mGTaskHashMap.get(gid);
                    // 若结点不为空，将其对应的google id从映射表中移除，然后建立google id到节点id的映射（通过hashmap）、gid和nid之间的映射表
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        // 若本地增加了内容，则远程也要增加内容
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    // 进行同步操作
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }

        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // go through remaining items
        // 扫描剩下的项目，逐个进行同步
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        // 迭代
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            // 在本地增加这些节点
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // mCancelled can be set by another thread, so we neet to check one by
        // one
        // clear local delete table
        // 终止标识有可能被其他进程改变，因此需要一个个进行检查
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // refresh local sync id
        // 更新同步表
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }

    }

    /**
     * 对文件夹进行同步，具体操作与之前的同步操作一致
     * @throws NetworkFailureException NetworkFailureException
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // for root folder
        try {
            // 使指针指向根文件夹的位置
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                // 获取gid所代表的节点
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // for system folder, only update remote name if necessary
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    }
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for call-note folder
        // 对于电话号码数据文件
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        // for system folder, only update remote name if
                        // necessary
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                        }
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for local existing folders
        // 同步已经存在的文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            // 远程添加
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            // 本地删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // for remote add folders
        // 对于在远程增添的内容，将其在本地同步
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        // 使用迭代器对远程增添的内容进行遍历
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        // 如果没有取消，在GTsk的客户端进行实例的提交更新
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
        }
    }

    // 内容同步，同步同步类型、节点以及数据库指针
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        // 根据不同的同步类型来选择不同的操作
        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                // 本地添加
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            // 远程删除
            case Node.SYNC_ACTION_DEL_LOCAL:
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            // 删除远程数据
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            // 更新本地数据
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            // 更新本地数据
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                updateRemoteNode(node, c);
                break;
            // 同步出错
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // merging both modifications maybe a good idea
                // right now just use local update simply
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:
                break;
            case Node.SYNC_ACTION_ERROR:
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }

    /**
     * 增加本地节点的操作，传入参量为待增添的节点
     * @param node Node
     * @throws NetworkFailureException NetworkFailureException
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // 若待增添节点为任务列表中的节点，进一步操作
        if (node instanceof TaskList) {
            // 在根目录中增加节点
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    // 在存放电话号码便签的文件夹中增加节点
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                // 若没有存放的文件夹，则将其放在根文件夹中
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            // 若待增添节点不是任务列表中的节点，进一步操作
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    // 获取对应便签的JSONObject对象
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        // 判断便签中是否有条目
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // the id is not available, have to create a new one
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                // 以下为判断便签中的数据条目
                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    // 依次删除存在的data的ID
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // the data id is not available, have to create
                                // a new one
                                data.remove(DataColumns.ID);
                            }
                        }
                    }

                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            // 找到父任务的ID号，并作为sqlNote的父任务的ID，没有父任务则报错
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        // create the local node
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        // update gid-nid mapping
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // update meta
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 更新本地节点，两个传入参数，一个是待更新的节点，一个是指向待增加位置的指针
     * @param node Node
     * @param c Cursor
     * @throws NetworkFailureException NetworkFailureException
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 新建一个sql节点并将内容存储进Node中
        SqlNote sqlNote;
        // update the note locally
        sqlNote = new SqlNote(mContext, c);
        // 利用待更新节点中的内容对数据库节点进行设置
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 设置父任务的ID，通过判断node是不是Task的实例
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        // 当不能找到该任务上一级的id时报错
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        // 设置该任务节点上一级的id
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);

        // update meta info
        // 更新远程的节点信息
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 添加远程结点，参数node是要添加远程结点的本地结点，c是数据库的指针
     * @param node Node
     * @param c Cursor
     * @throws NetworkFailureException NetworkFailureException
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 新建一个sql节点并将内容存储进Node中
        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        // update remotely
        // 如果sqlNote是节点类型，则设置好它的参数以及更新哈希表，再把节点更新到远程数据里
        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            GTaskClient.getInstance().createTask(task);
            n = (Node) task;

            // add meta
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            TaskList tasklist = null;

            // we need to skip folder if it has already existed
            // 当文件夹存在则跳过，若不存在则创建新的文件夹
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER) {
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            } else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER) {
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            } else {
                folderName += sqlNote.getSnippet();
            }

            // 通过迭代器对TaskList进行遍历
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }

            // no match we can add now
            // 若没有匹配的任务列表，则创建一个新的任务列表
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }

        // update local note
        // 进行本地节点的更新
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        // gid-id mapping
        // 进行gid与nid映射关系的更新
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 更新远程结点，参数node是要更新的结点，c是数据库的指针
     * @param node Node
     * @param c Cursor
     * @throws NetworkFailureException NetworkFailureException
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        // update remotely
        // 远程更新
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);

        // update meta
        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);

        // move task if necessary
        // 判断节点类型是否符合要求
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            // 若两个上一级任务列表不一致，进行任务的移动，从之前的任务列表中移动到该列表中
            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // clear local modified flag
        // 清除本地修改标记
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /**
     * 更新远程结点的数据，与上一个函数不同的是这里只更新数据
     * @param gid String
     * @param sqlNote SqlNote
     * @throws NetworkFailureException NetworkFailureException
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        // 判断节点类型是否符合，类型符合时才进行更新操作
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            // 若元数据组为空，则创建一个新的元数据组
            if (metaData != null) {
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
                // 若元数据组不为空，则进行更新
            } else {
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /**
     * 刷新本地便签id，从远程同步
     * @throws NetworkFailureException NetworkFailureException
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // get the latest gtask list
        // 获取最新的gtask列表
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            // 使指针指向列表中需要更新的任务
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                // 用迭代器不断从GTask的哈希表中删除node节点，设置新的content和新的resolcer并更新
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /**
     * 获取同步账号
     * @return String
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 取消同步，置mCancelled为true
     */
    public void cancelSync() {
        mCancelled = true;
    }
}
