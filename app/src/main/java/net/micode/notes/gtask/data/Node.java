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

import org.json.JSONObject;

/**
 * 同步操作的基础数据类型，定义了相关指示同步操作的常量
 */
public abstract class Node {
    /**
     * 本地和云端内容一致，无需更新,值为0
     */
    public static final int SYNC_ACTION_NONE = 0;

    /**
     * 在远程云端增加内容,值为1
     */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /**
     * 在本地增加内容，值为2
     */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /**
     * 在远程云端删除内容，值为3
     */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /**
     * 在本地删除内容，值为4
     */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /**
     * 将本地内容更新到远程云端，值为5
     */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /**
     * 将远程云端内容更新到本地，值为6
     */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /**
     * 同步出现冲突，值为7
     */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /**
     * 同步出现错误，值为8
     */
    public static final int SYNC_ACTION_ERROR = 8;

    /**
     * 最后一次修改时间
     */
    private String mGid;

    /**
     * 记录是否被删除
     */
    private String mName;

    /**
     * 最后一次修改时间
     */
    private long mLastModified;

    /**
     * 表明是否被删除
     */
    private boolean mDeleted;

    /**
     * 构造方法，进行初始化操作
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    /**
     * 获取创建信息
     * @param actionId int
     * @return JSONObject
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 获取更新活动
     * @param actionId int
     * @return JSONObject
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 创建相应的对象进行远端和本地同步操作
     * @param js JSONObject
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 创建相应对象进行本地操作
     * @param js JSONObject
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 声明JSONObject对象抽象类，从目录中获取本地JSON
     * @return JSONObject
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 获取同步行为代号
     * @param c Cursor
     * @return int
     */
    public abstract int getSyncAction(Cursor c);

    /**
     * 将gid的值赋给mgid
     * @param gid String
     */
    public void setGid(String gid) {
        this.mGid = gid;
    }

    /**
     * 将name的值赋给mName
     * @param name String
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * lastModified赋给mLastModified
     * @param lastModified long
     */
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    /**
     * 将deleted赋给mDeleted
     * @param deleted boolean
     */
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    /**
     * 获取mGrid的值
     * @return String
     */
    public String getGid() {
        return this.mGid;
    }

    /**
     * 获取mName的值
     * @return String
     */
    public String getName() {
        return this.mName;
    }

    /**
     * 获取mLastModified的值
     * @return long
     */
    public long getLastModified() {
        return this.mLastModified;
    }

    /**
     * 获取mDeleted的值
     * @return Boolean
     */
    public boolean getDeleted() {
        return this.mDeleted;
    }

}
