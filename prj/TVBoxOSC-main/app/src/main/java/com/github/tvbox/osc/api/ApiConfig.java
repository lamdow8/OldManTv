package com.github.tvbox.osc.api;

import android.app.Activity;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class ApiConfig {
    private static ApiConfig instance;
    private LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource;
    private ParseBean mDefaultParse;
    private List<LiveChannelGroup> liveChannelGroupList;
    private List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    private List<IJKCode> ijkCodes;
    private String spider = null;

    private SourceBean emptyHome = new SourceBean();

    private JarLoader jarLoader = new JarLoader();


    private ApiConfig() {
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        parseBeanList = new ArrayList<>();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    //zog
    public void loadLocalConfig(boolean useCache, LoadConfigCallback callback) {
        String my_txt ="";

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        File self = new File(root + "/tvbox_zog/zogtv.txt" );
        File selfDir = self.getParentFile();
        if (!selfDir.exists())
            selfDir.mkdirs();
        if (self.exists())
        {
            //优先用SD卡的/tvbox_zog/zogtv.txt
            my_txt = self.getAbsolutePath();
        }
        else
        {
            //不然用内置的/asset/zogtv.txt
            my_txt = assetCopy(useCache,"zogtv.txt");
            self = new File(my_txt);
        }
        //最差的时候用在线的loadJar
        LOG.e("loadLocalConfig path=" + my_txt);
        try {
            parseJson(my_txt, self);
            callback.success();
            return;
        } catch (Throwable th) {
            th.printStackTrace();
            callback.error("-1");
        }
    }

    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl.isEmpty()) {
            //zog
            loadLocalConfig(useCache,callback);
            //callback.error("-1");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        if (useCache && cache.exists()) {
            try {
                parseJson(apiUrl, cache);
                callback.success();
                return;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        String apiFix = apiUrl;
        if (apiUrl.startsWith("clan://")) {
            apiFix = clanToAddress(apiUrl);
        }
        OkGo.<String>get(apiFix)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String json = response.body();
                            parseJson(apiUrl, response.body());
                            try {
                                File cacheDir = cache.getParentFile();
                                if (!cacheDir.exists())
                                    cacheDir.mkdirs();
                                if (cache.exists())
                                    cache.delete();
                                FileOutputStream fos = new FileOutputStream(cache);
                                fos.write(json.getBytes("UTF-8"));
                                fos.flush();
                                fos.close();
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                            callback.success();
                        } catch (Throwable th) {
                            th.printStackTrace();
                            callback.error("解析配置失败");
                            return;
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (cache.exists()) {
                            try {
                                parseJson(apiUrl, cache);
                                callback.success();
                                return;
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                        }
                        callback.error("拉取配置失败\n" + (response.getException() != null ? response.getException().getMessage() : ""));
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result = "";
                        if (response.body() == null) {
                            result = "";
                        } else {
                            result = response.body().string();
                        }
                        if (apiUrl.startsWith("clan")) {
                            result = clanContentFix(clanToAddress(apiUrl), result);
                        }
                        return result;
                    }
                });
    }
    //zog
    public boolean isZogAdvanceMode() {

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String cachePath = root + "/tvbox_zog/"+"isAdvance" ;
        File cache = new File(cachePath);
        File cacheDir = cache.getParentFile();
        if (!cacheDir.exists())
            cacheDir.mkdirs();
        if (cache.exists())
        {
            return true;
        }
        return false;
    }
    //zog
    public String writeSD(String name,byte[] buffer) {

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String cachePath = root + "/tvbox_zog/"+name ;
        File cache = new File(cachePath);
        File cacheDir = cache.getParentFile();
        if (!cacheDir.exists())
            cacheDir.mkdirs();
        if (cache.exists())
        {
            cache.delete();
        }
        LOG.e("writeSD to path=" + cachePath);
        try {
            FileOutputStream fos = new FileOutputStream(cache);
            fos.write(buffer);
            fos.flush();
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cachePath;
    }
    //zog
    public boolean loadLocalLiveJson() {
        String my_json ="";

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        File self = new File(root + "/tvbox_zog/zoglive.json" );
        File selfDir = self.getParentFile();
        if (!selfDir.exists())
            selfDir.mkdirs();
        ByteArrayOutputStream bos =new ByteArrayOutputStream();
        if (self.exists())
        {
            //优先用SD卡的/tvbox_zog/zoglive.json
            LOG.e("loadLocalLiveJson path=" + self.getAbsolutePath());
            try {
                FileInputStream fis = new FileInputStream(self);
                byte[] buffer = new byte[10240];
                int byteCount;
                while ((byteCount = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, byteCount);
                }
                bos.flush();
                bos.close();
                fis.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return  false;
            } catch (IOException e) {
                e.printStackTrace();
                return  false;
            }
        }
        else
        {
            //不然用内置的/asset/zoglive.json
            LOG.e("loadLocalLiveJson path=getAssets " + "zoglive.json");
            try {
                InputStream fis = App.getInstance().getAssets().open("zoglive.json");
                byte[] buffer = new byte[10240];
                int byteCount;
                while ((byteCount = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, byteCount);
                }
                bos.flush();
                bos.close();
                fis.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return  false;
            } catch (IOException e) {
                e.printStackTrace();
                return  false;
            }
        }
        //最差的时候用在线的loadJar
        JsonArray livesArray = new Gson().fromJson(bos.toString(), JsonArray.class);
        loadLives(livesArray);
        return  true;
    }

    //zog
    public boolean loadLocalLiveTxt() {
        String my_txt ="";

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        File self = new File(root + "/tvbox_zog/zoglive.txt" );
        File selfDir = self.getParentFile();
        if (!selfDir.exists())
            selfDir.mkdirs();
        ByteArrayOutputStream bos =new ByteArrayOutputStream();
        if (self.exists())
        {
            //优先用SD卡的/tvbox_zog/zoglive.txt
            LOG.e("loadLocalLiveTxt path=" + self.getAbsolutePath());
            try {
                FileInputStream fis = new FileInputStream(self);
                byte[] buffer = new byte[10240];
                int byteCount;
                while ((byteCount = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, byteCount);
                }
                bos.flush();
                bos.close();
                fis.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return  false;
            } catch (IOException e) {
                e.printStackTrace();
                return  false;
            }
        }
        else
        {
            //不然用内置的/asset/zoglive.txt
            LOG.e("loadLocalLiveTxt path=getAssets " + "zoglive.txt");
            try {
                InputStream fis = App.getInstance().getAssets().open("zoglive.txt");
                byte[] buffer = new byte[10240];
                int byteCount;
                while ((byteCount = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, byteCount);
                }
                bos.flush();
                bos.close();
                fis.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return  false;
            } catch (IOException e) {
                e.printStackTrace();
                return  false;
            }
        }

        String json = live2json(bos.toString());
        //最差的时候用在线的loadJar
        JsonArray livesArray = new Gson().fromJson(json, JsonArray.class);
        loadLives(livesArray);
        return  true;
    }

    //zog first load txt
    public void loadLocalLive() {
        if (!loadLocalLiveTxt()) {
            loadLocalLiveJson();
        }
    }
    //zog
    public String assetCopy(boolean useCache,String assetname) {

        String cachePath=App.getInstance().getFilesDir().getAbsolutePath() + "/"+assetname;
        File cache = new File(cachePath);
        File cacheDir = cache.getParentFile();
        if (!cacheDir.exists())
            cacheDir.mkdirs();
        if (cache.exists())
        {
            if(useCache)
            {
                return cachePath;
            }
            else
            {
                cache.delete();
            }
        }
        LOG.e("assetCopy to path=" + cachePath);
        try {
            InputStream fis = App.getInstance().getAssets().open(assetname);
            FileOutputStream fos = new FileOutputStream(cache);

            byte[] buffer = new byte[10240];
            int byteCount;
            while ((byteCount = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, byteCount);
            }
            fos.flush();

            fis.close();
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cachePath;
    }
    //zog
    public void loadLocalJar(boolean useCache,LoadConfigCallback callback) {
        String my_spider ="";

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        File self = new File(root + "/tvbox_zog/zogspider.jar" );
        File selfDir = self.getParentFile();
        if (!selfDir.exists())
            selfDir.mkdirs();
        if (self.exists())
        {
            //优先用SD卡的/tvbox_zog/zogspider.jar
             my_spider = self.getAbsolutePath();
        }
        else
        {
            //不然用内置的/asset/zogspider.jar
             my_spider = assetCopy(useCache,"zogspider.jar");
        }
        //最差的时候用在线的loadJar
        LOG.e("loadLocalJar path=" + my_spider);
        if (jarLoader.load(my_spider)) {
            callback.success();
        } else {
            callback.error("");
        }
    }

    public void loadJar(boolean useCache, String spider, LoadConfigCallback callback) {
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");

        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5))) {
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                } else {
                    callback.error("");
                }
                return;
            }
        }

        OkGo.<File>get(jarUrl).execute(new AbsCallback<File>() {

            @Override
            public File convertResponse(okhttp3.Response response) throws Throwable {
                File cacheDir = cache.getParentFile();
                if (!cacheDir.exists())
                    cacheDir.mkdirs();
                if (cache.exists())
                    cache.delete();
                FileOutputStream fos = new FileOutputStream(cache);
                fos.write(response.body().bytes());
                fos.flush();
                fos.close();
                return cache;
            }

            @Override
            public void onSuccess(Response<File> response) {
                if (response.body().exists()) {
                    if (jarLoader.load(response.body().getAbsolutePath())) {
                        callback.success();
                    } else {
                        callback.error("");
                    }
                } else {
                    callback.error("");
                }
            }

            @Override
            public void onError(Response<File> response) {
                super.onError(response);
                callback.error("");
            }
        });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        System.out.println("从本地缓存加载" + f.getAbsolutePath());
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s = "";
        while ((s = bReader.readLine()) != null) {
            sb.append(s + "\n");
        }
        bReader.close();
        parseJson(apiUrl, sb.toString());
    }

    private void parseJson(String apiUrl, String jsonStr) {
        JsonObject infoJson = new Gson().fromJson(jsonStr, JsonObject.class);
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        // 远端站点源
        SourceBean firstSite = null;
        for (JsonElement opt : infoJson.get("sites").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.get("name").getAsString().trim());
            sb.setType(obj.get("type").getAsInt());
            sb.setApi(obj.get("api").getAsString().trim());
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            sb.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            if (firstSite == null)
                firstSite = sb;
            sourceBeanList.put(siteKey, sb);
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null)
                setSourceBean(firstSite);
            else
                setSourceBean(sh);
        }
        // 需要使用vip解析的flag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析地址
        for (JsonElement opt : infoJson.get("parses").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            ParseBean pb = new ParseBean();
            pb.setName(obj.get("name").getAsString().trim());
            pb.setUrl(obj.get("url").getAsString().trim());
            String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
            pb.setExt(ext);
            pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
            parseBeanList.add(pb);
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse))
                for (ParseBean pb : parseBeanList) {
                    if (pb.getName().equals(defaultParse))
                        setDefaultParse(pb);
                }
            if (mDefaultParse == null)
                setDefaultParse(parseBeanList.get(0));
        }
        // 直播源
        liveChannelGroupList.clear();           //修复从后台切换重复加载频道列表
        try {
            String lives = infoJson.get("lives").getAsJsonArray().toString();
            int index = lives.indexOf("proxy://");
            if (index != -1) {
                int endIndex = lives.lastIndexOf("\"");
                String url = lives.substring(index, endIndex);
                url = DefaultConfig.checkReplaceProxy(url);

                //clan
                String extUrl = Uri.parse(url).getQueryParameter("ext");
                if (extUrl != null && !extUrl.isEmpty()) {
                    String extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                    if (extUrlFix.startsWith("clan://")) {
                        extUrlFix = clanContentFix(clanToAddress(apiUrl), extUrlFix);
                        extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        url = url.replace(extUrl, extUrlFix);
                    }
                }
                LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
                liveChannelGroup.setGroupName(url);
                liveChannelGroupList.add(liveChannelGroup);
            } else {
                //zog
                index = lives.indexOf("use_zog_local");
                if (index != -1)
                {
                    loadLocalLive();
                }
                else
                {
                    loadLives(infoJson.get("lives").getAsJsonArray());
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        // 广告地址
        for (JsonElement host : infoJson.getAsJsonArray("ads")) {
            AdBlocker.addAdHost(host.getAsString());
        }
        // IJK解码配置
        boolean foundOldSelect = false;
        String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "");
        ijkCodes = new ArrayList<>();
        for (JsonElement opt : infoJson.get("ijk").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            String name = obj.get("group").getAsString();
            LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
            for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                JsonObject cObj = (JsonObject) cfg;
                String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                String val = cObj.get("value").getAsString();
                baseOpt.put(key, val);
            }
            IJKCode codec = new IJKCode();
            codec.setName(name);
            codec.setOption(baseOpt);
            if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                codec.selected(true);
                ijkCodec = name;
                foundOldSelect = true;
            } else {
                codec.selected(false);
            }
            ijkCodes.add(codec);
        }
        if (!foundOldSelect && ijkCodes.size() > 0) {
            ijkCodes.get(0).selected(true);
        }
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1)
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else
                liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1)
                        sourceNames.add(splitText[1]);
                    else
                        //sourceNames.add("源" + Integer.toString(sourceIndex)); //zog
                        sourceNames.add("");
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            liveChannelGroupList.add(liveChannelGroup);
        }
    }

    public String getSpider() {
        return spider;
    }

    public Spider getCSP(SourceBean sourceBean) {
        return jarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt());
    }

    public Object[] proxyLocal(Map param) {
        return jarLoader.proxyInvoke(param);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void retry();

        void error(String msg);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key))
            return null;
        return sourceBeanList.get(key);
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null)
            this.mDefaultParse.setDefault(false);
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        return vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return ijkCodes.get(0);
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://", fix);
    }


    //zog :from CatVodTVSpider project: TxtSubscribe class
    public  void TxtSubscribe_parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> allLives, String txt) {
        try {
            BufferedReader br = new BufferedReader(new StringReader(txt));
            String line = br.readLine();
            LinkedHashMap<String, ArrayList<String>> noGroup = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> groupLives = noGroup;
            while (line != null) {
                if (line.trim().isEmpty()) {
                    line = br.readLine();
                    continue;
                }
                String[] lineInfo = line.split(",");
                if (lineInfo.length < 2) {
                    line = br.readLine();
                    continue;
                }
                if (line.contains("#genre#")) {
                    String group = lineInfo[0].trim();
                    if (!allLives.containsKey(group)) {
                        groupLives = new LinkedHashMap<>();
                        allLives.put(group, groupLives);
                    } else {
                        groupLives = allLives.get(group);
                    }
                } else {
                    String title = lineInfo[0].trim();
                    String[] urlMix = lineInfo[1].trim().split("#");
                    for (int j = 0; j < urlMix.length; j++) {
                        String url = urlMix[j].trim();
                        if (url.isEmpty())
                            continue;
                        if (url.startsWith("http") || url.startsWith("rtsp") || url.startsWith("rtmp")) {
                            ArrayList<String> urls = null;
                            if (!groupLives.containsKey(title)) {
                                urls = new ArrayList<>();
                                groupLives.put(title, urls);
                            } else {
                                urls = groupLives.get(title);
                            }
                            if (!urls.contains(url))
                                urls.add(url);
                        } else {
                            // SpiderDebug.log("Skip " + url);
                        }
                    }
                }
                line = br.readLine();
            }
            br.close();
            if (!noGroup.isEmpty()) {
                allLives.put("未分组", noGroup);
            }
        } catch (Throwable th) {

        }
    }

    //zog :from CatVodTVSpider project: TxtSubscribe class
    public static String TxtSubscribe_live2Json(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> lives) {
        JSONArray groups = new JSONArray();
        Iterator<String> groupKeys = lives.keySet().iterator();
        while (groupKeys.hasNext()) {
            String group = groupKeys.next();
            JSONArray channels = new JSONArray();
            LinkedHashMap<String, ArrayList<String>> allChannel = lives.get(group);
            if (allChannel.isEmpty())
                continue;
            Iterator<String> channelKeys = allChannel.keySet().iterator();
            while (channelKeys.hasNext()) {
                String channel = channelKeys.next();
                ArrayList<String> allUrls = allChannel.get(channel);
                if (allUrls.isEmpty())
                    continue;
                JSONArray urls = new JSONArray();
                for (int i = 0; i < allUrls.size(); i++) {
                    urls.put(allUrls.get(i));
                }
                JSONObject newChannel = new JSONObject();
                try {
                    newChannel.put("name", channel);
                    newChannel.put("urls", urls);
                } catch (JSONException e) {
                }
                channels.put(newChannel);
            }
            JSONObject newGroup = new JSONObject();
            try {
                newGroup.put("group", group);
                newGroup.put("channels", channels);
            } catch (JSONException e) {
            }
            groups.put(newGroup);
        }
        return groups.toString();
    }

    //zog :from CatVodTVSpider project: TxtSubscribe class
    public String live2json(String txt)
    {
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> allLives = new LinkedHashMap<>();
        TxtSubscribe_parse(allLives, txt);
        return TxtSubscribe_live2Json(allLives);
    }

}