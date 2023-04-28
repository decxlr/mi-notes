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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;


public class NoteWidgetProvider_4x extends NoteWidgetProvider {         /*定义4*4的widget*/
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {      /*对窗口更新进行重载*/
        super.update(context, appWidgetManager, appWidgetIds);
    }

    protected int getLayoutId() {               /*返回窗口位置信息*/
        return R.layout.widget_4x;
    }

    @Override
    protected int getBgResourceId(int bgId) {   /*获取背景id*/
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    @Override
    protected int getWidgetType() {             /*返回窗口类型*/
        return Notes.TYPE_WIDGET_4X;
    }
}
