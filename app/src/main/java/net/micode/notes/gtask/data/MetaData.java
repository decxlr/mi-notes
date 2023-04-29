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
//标注该文件遵循 Apache 2.0 开源许可

package net.micode.notes.gtask.data;

import android.database.Cursor;
//基于数据库服务的类
import android.util.Log;
//日志工具类
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
// Json使用失败异常处理
import org.json.JSONObject;
//导入Json对象库

/**
 * 元数据类 继承于task类，task类也在data包里，主要是描述数据属性的信息
 * @author X
 */
public class MetaData extends Task {

    /**
     * 通过调用getSimpleName()方法将类的简写名称存入字符串TAG中
     */
    private final static String TAG = MetaData.class.getSimpleName();

    /**
     * 创建私有变量mRelatedGid，并初始化为null
     */
    private String mRelatedGid = null;

    /**
     * 设置数据，即生成元数据库。通过调用JSONObject库函数put ()，
     * Task类中的setNotes ()和setName ()函数来实现这一功能
     * @param gid String
     * @param metaInfo JSONObject
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将这对键值放入metaInfo这个JSONObject对象中
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            // 若出现异常，则打印日志信息
            Log.e(TAG, "failed to put related gid");
        }
        // 设置便签，将json类的metaInfo转换为String类型
        setNotes(metaInfo.toString());
        // 设置GTask的名字
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取关联Gid
     * @return String
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断是否值得存放，若数据非空则返回真值
     * @return boolean
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 重写父类中的setContentByRemoteJSON ()方法，
     * 使用远程json数据对象设置元数据内容
     * @param js JSONObject
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                // 创建新json对象metaInfo，调用trim方法去掉getNotes方法的返回值的首尾空格
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                // 若出现异常，打印日志信息，并将mRelatedGid置空
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 重写父类的setContentByLocalJSON方法
     * 使用本地json数据对象设置元数据内容，若用到，则抛出异常
     * @param js JSONObject
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }


    /**
     * 重写父类的getLocalJSONFromContent方法
     * 从元数据内容中获取本地json对象，抛出异常
     * @return JSONObject
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 重写父类的getSyncAction方法
     * 获取同步动作状态，一般不会用到，若用到，则抛出异常
     * @param c Cursor
     * @return int
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}
