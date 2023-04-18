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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


public class GTaskClient {
    /**
     * getSimpleName的作用是得到类的简写名称。这里是将简写赋值给TAG字符串，便于标识这个类
     */
    private static final String TAG = GTaskClient.class.getSimpleName();

    /**
     * 谷歌邮箱的URL
     */
    private static final String GTASK_URL = "https://mail.google.com/tasks/";

    /**
     * 获取的url
     */
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";

    /**
     * 传递的url
     */
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    /**
     * 后续使用的参数以及变量
     */
    private static GTaskClient mInstance = null;

    private DefaultHttpClient mHttpClient;

    private String mGetUrl;

    private String mPostUrl;

    private long mClientVersion;

    private boolean mLoggedin;

    private long mLastLoginTime;

    private int mActionId;

    private Account mAccount;

    private JSONArray mUpdateArray;

    /**
     * 无参构造方法。初始化操作
     */
    private GTaskClient() {
        mHttpClient = null;
        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;
    }

    /**
     * 获取实例，如果当前没有示例，则新建一个登陆的gtask，如果有直接返回
     * @return GTaskClient
     */
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 用来实现登录操作的函数，传入的参数是一个Activity
     * @param activity Activity
     * @return boolean
     */
    public boolean login(Activity activity) {
        // we suppose that the cookie would expire after 5 minutes
        // then we need to re-login
        // 时间间隔为1000ns * 60 * 5 = 5min 即间隔5分钟时间
        final long interval = 1000 * 60 * 5;
        // 判断距离上次登录的时间间隔，若超过5分钟，则重置登录状态
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }

        // need to re-login after account switch
        // 需要重新登录
        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                        .getSyncAccountName(activity))) {
            mLoggedin = false;
        }

        /**
         * 如果登录成功，打印日志（已经登录），并返回true（登录成功），函数结束，不再执行下面的语句。
         */
        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        // 更新登录时间为系统当前时间
        mLastLoginTime = System.currentTimeMillis();
        // 获取登录令牌，判断是否登入google账号
        String authToken = loginGoogleAccount(activity, false);
        // 如果登录失败，显示login google account failed
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // login with custom domain if necessary
        // 在谷歌账号登录成功的情况下再登录gtask账号
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase()
                .endsWith("googlemail.com"))) {
            // 新建一个url
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            // 语句：返回@第一次出现的位置并把位置+1后记录在index里
            int index = mAccount.name.indexOf('@') + 1;
            // 提取了index开始到后面的字符，也就是账户名的后缀
            String suffix = mAccount.name.substring(index);
            // 均为字符串操作来构建url链接
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            // 成功登入
            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // try to login with google official url
        //  若前面的尝试失败，则尝试使用官方的域名登录
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }

    /**
     * 用以具体实现登录Google账号的方法，方法返回账号令牌
     * AccountManager：账户管理器，辅助管理账户
     * @param activity Activity
     * @param invalidateToken boolean
     * @return String
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        // 给用户提供注册账号用的接口
        AccountManager accountManager = AccountManager.get(activity);
        // 将所有以com.google结尾的账号存入accounts数组中
        Account[] accounts = accountManager.getAccountsByType("com.google");

        // 如果没有这样的账号，输出日志信息“无有效的google账户”
        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        // 如果找到了合适的用户名，就将其记录下来，下次自动登录，否则返回登录失败
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // get the token now
        // 从账户中获取目标账户的令牌
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            // bundle是一个key-value对，这里获取目标账户的最终结果集
            Bundle authTokenBundle = accountManagerFuture.getResult();
            // 这里获取bundle类的对象的String串并赋值给令牌对象
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            // 如果是非法的令牌，那么废除这个账号，取消登录状态
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken);
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }

        return authToken;
    }

    // 用于判断令牌对于登陆gtask账号是否有效
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // maybe the auth token is out of date, now let's invalidate the
            // token and try again
            // 删除过一个无效的authToken，申请一个新的后再次尝试登陆
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }

            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        return true;
    }

    // 登录gtask的实现函数，根据令牌判断是否登陆成功
    private boolean loginGtask(String authToken) {
        // 连接超时为10000毫秒，即10秒
        int timeoutConnection = 10000;
        //  端口超时的值为15秒
        int timeoutSocket = 15000;
        // 申请一个httpParameters参数类的对象
        HttpParams httpParameters = new BasicHttpParams();
        // 设置连接超时的时间
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        // 设置端口超时的时间
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        // 新建一个默认客户端
        mHttpClient = new DefaultHttpClient(httpParameters);
        // 为cookie申请存储对象
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        // 设置本地cookie
        mHttpClient.setCookieStore(localBasicCookieStore);
        // 设置http协议1.1中的一个header属性Expect 100 Continue
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // login gtask
        try {
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // get the cookie now
            // 获取cookie值
            List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
            boolean hasAuthCookie = false;
            // 遍历cookies集合中的每个Cookie对象，如果有一个Cookie对
            // 象的名中含有GTL，hasAuthCookie被赋True
            for (Cookie cookie : cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true;
                }
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie");
            }

            // get the client version
            // 获取客户端版本
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            // 设置客户端版本
            JSONObject js = new JSONObject(jsString);
            mClientVersion = js.getLong("v");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            // simply catch all exceptions
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }

        return true;
    }

    // 获取actionID然后加一
    private int getActionId() {
        return mActionId++;
    }

    /**
     * 创建一个用来保存URL的httppost对象
     * @return HttpPost
     */
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1");
        return httpPost;
    }

    /**
     * 获取服务器响应的数据，主要通过方法getContentEncoding来获取网上资源，返回这些资源
     * @param entity
     * @return String
     * @throws IOException IOException
     */
    private String getResponseContent(HttpEntity entity) throws IOException {
        String contentEncoding = null;
        // 如果获取的内容编码不为空，给内容编码赋值，显示encoding：内容编码
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }

        // HTTP定义了一些标准的内容编码类型，并允许用扩展的形式添加更多的编码。
        InputStream input = entity.getContent();
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            input = new GZIPInputStream(entity.getContent());
        } else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);
        }

        // 完成将字节流数据内容进行存储的功能
        try {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();

            while (true) {
                String buff = br.readLine();
                if (buff == null) {
                    return sb.toString();
                }
                sb = sb.append(buff);
            }
        } finally {
            input.close();
        }
    }

    /**
     * 利用JSON发送请求，返回获取的内容
     * @param js JSONObject
     * @return JSONObject
     * @throws NetworkFailureException NetworkFailureException
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        // 未登录，输出提示信息
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        // 实例化一个httpPost的对象用来向服务器传输数据，发送在js里请求的内容
        HttpPost httpPost = createHttpPost();
        try {
            // LinkedList 类：是一个继承于AbstractSequentialList的双向链表
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            list.add(new BasicNameValuePair("r", js.toString()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            httpPost.setEntity(entity);

            // execute the post
            // 执行发布的请求
            HttpResponse response = mHttpClient.execute(httpPost);
            String jsString = getResponseContent(response.getEntity());
            return new JSONObject(jsString);

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("unable to convert response content to jsonobject");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("error occurs when posting request");
        }
    }

    /**
     * 创建单个任务，通过json获取TASK中的内容并创建对应的jsPost，通过postRequest方法获取任务的返回信息，使用setGid方法设置task的new_id
     * @param task Task
     * @throws NetworkFailureException NetworkFailureException
     */
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // post
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    /**
     * 创建任务列表
     * @param tasklist TaskList
     * @throws NetworkFailureException NetworkFailureException
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // post
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }

    /**
     * 提交更新数据，还是利用JSON
     * @throws NetworkFailureException NetworkFailureException
     */
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();

                // action_list
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // client_version
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                // post操作
                postRequest(jsPost);
                mUpdateArray = null;
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }

    /**
     * 添加更新注意事项
     * @param node Node
     * @throws NetworkFailureException NetworkFailureException
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // too many update items may result in an error
            // set max to 10 items
            // 提交后更新
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate();
            }

            if (mUpdateArray == null) {
                mUpdateArray = new JSONArray();
            }
            // 将更新节点加入列表
            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    /**
     * 移动一个任务，通过getGid获取task所属的Id，通过JSONObject和postRequest实现
     * @param task Task
     * @param preParent TaskList
     * @param curParent TaskList
     * @throws NetworkFailureException NetworkFailureException
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate();
        // 操作列表
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());
            if (preParent == curParent && task.getPriorSibling() != null) {
                // put prioring_sibing_id only if moving within the tasklist and
                // it is not the first one
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());
            if (preParent != curParent) {
                // put the dest_list only if moving between tasklists
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }

    /**
     * 删除节点操作
     * @param node Node
     * @throws NetworkFailureException NetworkFailureException
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        // 新建jsPost，把除了node的其他节点都放入jsPost，并提交
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            node.setDeleted(true);
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }

    /**
     * 获取任务列表，首先通过getURI在网上获取数据，在截取所需部分内容返回
     * @return JSONArray
     * @throws NetworkFailureException NetworkFailureException
     */
    public JSONArray getTaskLists() throws NetworkFailureException {
        // 如果没有登录则显示请先登录，抛出一个异常
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        // 使用url实例化一个获取对象
        try {
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // get the task list
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);
        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task lists: handing jasonobject failed");
        }
    }

    /**
     * 对于已经获取的任务列表，可以通过其id来获取到
     * @param listGid String
     * @return JSONArray
     * @throws NetworkFailureException NetworkFailureException
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        // 设置为传入的listGid
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    // 获得同步账户
    public Account getSyncAccount() {
        return mAccount;
    }

    // 重置更新内容
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}
