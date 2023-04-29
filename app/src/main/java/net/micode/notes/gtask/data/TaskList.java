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

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * 创建继承于Node类的TaskList类
 */
public class TaskList extends Node {
    /**
     * 调用getSimpleName()方法获取名字并赋值给TAG
     */
    private static final String TAG = TaskList.class.getSimpleName();

    /**
     * 当前TaskList的指针
     */
    private int mIndex;

    /**
     * 类中主要的保存数据的单元，用来实现一个以Task为元素的ArrayList
     */
    private ArrayList<Task> mChildren;

    /**
     * 构造方法，调用父类构造方法，同时初始化自身特有元素
     */
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    /**
     * 生成并返回一个包含了一定数据的JSONObject实体
     * @param actionId int
     * @return JSONObject
     */
    @Override
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            // 操作列表
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id
            // 调用put放入编号
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // entity_delta
            /*新建一个新的JSONObject对象，用于存放一些不同的数据。最后这个结构会被放入之前创建的js对象中，一起返回*/
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 获取上传指令
     * @param actionId int
     * @return JSONObject
     */
    @Override
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // id
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta
            /*创建一个 JSONObject 的实例化对象 entity（实体）*/
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 通过云端 JSON 数据设置实例化对象 js 的内容
     * @param js JSONObject
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // id
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // last_modified
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 通过本地 JSON 数据设置对象 js 内容
     * @param js JSONObject
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        /*若 js 创建失败或 js 中不存在 META_HEAD_NOTE信息*/
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        // NullPointerException这个异常出现在处理对象时对象不存在但又没有捕捉到进行处理的时候
        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            // 若为一般类型的文件夹
            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                // 设置名称：MIUI系统文件夹前缀+文件夹名称
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
                //若为系统类型文件夹
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 若为根目录文件夹
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER) {
                    // 设置名称：MIUI系统文件夹前缀+默认文件夹名称
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                    // 若为通话记录文件夹
                } else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER) {
                    // 设置名称：MIUI系统文件夹前缀+通话便签文件夹名称
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                } else {
                    Log.e(TAG, "invalid system folder");
                }
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 通过 Content 机制获取本地 JSON 数据
     * @return JSONObject
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();
            // 如果这个文件名字是以"[MIUI_Notes]"开头，说明文件名字应该去掉这个前缀
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)) {
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            }
            folder.put(NoteColumns.SNIPPET, folderName);

            // 当获取的文件夹名称是以"Default"或"Call_Note开头，则为系统文件夹。否则为一般文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE)) {
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            } else {
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
            }

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 通过 cursor 获取同步信息
     * @param c Cursor
     * @return int
     */
    @Override
    public int getSyncAction(Cursor c) {
        try {
            // 若本地记录未修改
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // there is no local update
                // 最近一次修改的 ID 匹配成功，返回无的同步行为
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side
                    return SYNC_ACTION_NONE;
                } else {
                    // apply remote to local
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // validate gtask id
                // 如果获取的ID不匹配，返回同步动作失败
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // for folder conflicts, just apply local modification
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 获取子任务数量
     * @return int
     */
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 在当前任务表末尾添加新的任务
     * @param task Task
     * @return boolean
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        // 任务非空且任务表中不存在该任务
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // need to set prior sibling and parent
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在当前任务表的指定位置添加新的任务
     * @param task Task
     * @param index int
     * @return boolean
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // update the task list
            Task preTask = null;
            Task afterTask = null;
            if (index != 0) {
                preTask = mChildren.get(index - 1);
            }
            if (index != mChildren.size() - 1) {
                afterTask = mChildren.get(index + 1);
            }

            task.setPriorSibling(preTask);
            if (afterTask != null) {
                afterTask.setPriorSibling(task);
            }
        }

        return true;
    }

    /**
     * 删除任务表中的子任务
     * @param task Task
     * @return boolean
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // reset prior sibling and parent
                task.setPriorSibling(null);
                task.setParent(null);

                // update the task list
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 将当前TaskList中含有的某个Task移到index位置
     * @param task Task
     * @param index int
     * @return boolean
     */
    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index) {
            return true;
        }
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 按gid寻找Task
     * @param gid String
     * @return Task
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取指定Task的index
     * @param task Task
     * @return int
     */
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    /**
     * 通过index获取子任务
     * @param index int
     * @return Task
     */
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * 通过gid获取子任务
     * @param gid String
     * @return Task
     */
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid)) {
                return task;
            }
        }
        return null;
    }

    /**
     * 获取子任务列表
     * @return ArrayList<Task>
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    /**
     * 设置任务索引
     * @param index int
     */
    public void setIndex(int index) {
        this.mIndex = index;
    }

    /**
     * 获取任务索引
     * @return int
     */
    public int getIndex() {
        return this.mIndex;
    }
}
