package com.chen.service.wx;

import com.chen.dao.mapper.WXUserConcactMapper;
import com.chen.dao.mapper.WXUserMapper;
import com.chen.entity.WXUser;
import com.chen.entity.WXUserConcact;
import com.chen.model.WXLoginParamModel;
import com.chen.model.WXUserModel;
import com.chen.utils.http.HttpUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.http.client.CookieStore;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.*;

@Service
public class WeChatService {
    private static Logger Log = LoggerFactory.getLogger(WeChatService.class);

    /**
     * 登录后获取跳转url
     *
     * @param qrcodeUrl 二维码地址
     */
    public String scanThenGetRedirectUrl(String qrcodeUrl) {
        long timeStramp = new Date().getTime();
        int timeStrampReverse = (int) ~timeStramp;
        String uuid = qrcodeUrl.substring(35, qrcodeUrl.length());
        String loginUrl = "https://login.wx.qq.com/cgi-bin/mmwebwx-bin/login?loginicon=true&uuid="
                + uuid + "&tip=0&r=" + timeStrampReverse + "&_=" + timeStramp;
        String loginResult = HttpUtil.getByUTF8(loginUrl, null);
        String loginCode = loginResult.substring(12, 15);
        if (loginCode.equals("200")) {
            System.out.println(loginResult);
            return loginResult.substring(38, loginResult.length() - 2);
        } else {
            System.out.println("扫描失败");
            throw new RuntimeException("扫描失败，尚未登录");
        }
    }

    /**
     * 根据获取的跳转链接获取cookiestore来保存登录信息
     * 以及获取其它信息所用的参数
     */
    public WXLoginParamModel getLoginParamByRedirectUrl(String redirectUrl) {
        Map<String, Object> resultAndCookieStore = HttpUtil.getByUTF8AndStoreCookie(redirectUrl + "&fun=new&version=v2");
        WXLoginParamModel wxLoginParamModel=null;
        try {
            wxLoginParamModel=parseLoginParamStr2WXLoginParamModel((String) resultAndCookieStore.get("result"));
            wxLoginParamModel.setCookieStore( (CookieStore) resultAndCookieStore.get("cookieStore"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wxLoginParamModel;
    }


    /**
     * 将登录参数转化为model
     */
    public WXLoginParamModel parseLoginParamStr2WXLoginParamModel(String loginParamStr) throws Exception {
        try {
            Document document = DocumentHelper.parseText(loginParamStr);
            Element xml = document.getRootElement();
            Iterator<Element> iter = xml.elementIterator();
            Map<String, String> loginParam = Maps.newHashMap();
            while (iter.hasNext()) {
                try {
                    Element ele = iter.next();
                    loginParam.put(ele.getName(), ele.getText());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            WXLoginParamModel wxLoginParamModel = new WXLoginParamModel();
            BeanUtils.copyProperties(wxLoginParamModel, loginParam);
            return wxLoginParamModel;
        } catch (DocumentException e) {
            throw e;
        }

    }

    /**
     * 微信登陆后 获取初始化信息
     */
    public String getInitInfo(WXLoginParamModel model, CookieStore cookieStore) {
        String url = "https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=" + new Date().getTime() + "&pass_ticket=" + model.getPass_ticket();
        Map<String, String> baseRequest = Maps.newHashMap();
        baseRequest = setBaseRequest(model, baseRequest);
        Map<String, Object> requestParam = Maps.newHashMap();
        requestParam.put("BaseRequest", baseRequest);
        //result中包含初始化信息  最近联系人等等
        String result = HttpUtil.postJsonWithCookies(url, new Gson().toJson(requestParam),
                null, cookieStore);
        Gson gson = new Gson();
        Map<String, Object> map = gson.fromJson(result, Map.class);
        Map<String, Object> userMap = (Map<String, Object>) map.get("User");
        WXUserModel user = new WXUserModel();
        try {
            user = getWCUserModelByContactMap(userMap);
        } catch (Exception e) {
            Log.error(e.getMessage());
        }
        if(user!=null){
            WXUser wxUser=wxUserMapper.getUserByUin(user.getUin());
            if(wxUser==null){
                wxUserMapper.insertByModel(user);
            }
        }
        return user.getUin();
    }

    @Resource
    private WXUserMapper wxUserMapper;

    public List<WXUserModel> listWXUserModel(WXLoginParamModel wxLoginParamModel, CookieStore cookieStore,String uin) {
        String wxContactStr = listWxContact(wxLoginParamModel, cookieStore);
        return listWXUserModelByWXContactStr(wxContactStr,uin);
    }

    public String listWxContact(WXLoginParamModel wxLoginParamModel, CookieStore cookieStore) {
        String url = "https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxgetcontact?lang=zh_CN&pass_ticket=" +
                wxLoginParamModel.getPass_ticket() +
                "&r=" + new Date().getTime() +
                "&seq=0&skey=" + wxLoginParamModel.getSkey();
        return HttpUtil.getByUTF8(url, cookieStore);
    }

    @Resource
    private WXUserConcactMapper wxUserConcactMapper;
    public List<WXUserModel> listWXUserModelByWXContactStr(String wxContactStr,String uin) {
        Gson gson = new Gson();
        Map<String, Object> contactMap = gson.fromJson(wxContactStr, Map.class);
        List<Map<String, Object>> contactList = (List<Map<String, Object>>) contactMap.get("MemberList");
        List<WXUserModel> wxUserModels = Lists.newArrayList();
        for (Map<String, Object> userMap : contactList) {
            try {
                WXUserModel wxUserModel = getWCUserModelByContactMap(userMap);
                WXUserConcact wxUserConcact=new WXUserConcact();
                wxUserConcact.setUnionid(uin);
                BeanUtils.copyProperties(wxUserConcact,wxUserModel);
                WXUserConcact userConcact=wxUserConcactMapper.getByUnionIdAndNickName(wxUserConcact);
                if(userConcact==null) {
                    wxUserConcactMapper.insert(wxUserConcact);
                }
                wxUserModels.add(wxUserModel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return wxUserModels;
    }

    /**
     * 将map转化为model
     */
    public WXUserModel getWCUserModelByContactMap(Map<String, Object> contactMap) throws Exception {

        Set<String> keys = contactMap.keySet();
        WXUserModel wxUserModel = new WXUserModel();
        for (String key : keys) {
            Class clazz = wxUserModel.getClass();
            try {
                if (key.equalsIgnoreCase("MemberList")) {
                    continue;
                }
                Method method = clazz.getMethod("set" + key, String.class);
                String val = null;
                try {
                    Double douVal = (Double) contactMap.get(key);
                    Long longVal = douVal.longValue();
                    val = longVal.toString();
                } catch (Exception e) {
                    val = (String) contactMap.get(key);
                }
                method.invoke(wxUserModel, val);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return wxUserModel;
    }

    public static WXUserModel getByUserName(List<WXUserModel> wxUserModelList, String nickName) {
        try {
            for (WXUserModel wxUserModel : wxUserModelList) {
                if (wxUserModel.getNickName().equals(nickName) || wxUserModel.getRemarkName().equals(nickName)) {
                    return wxUserModel;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String sendWXMsg(WXUserModel selfUserModel, WXUserModel wxUserModel, String content, CookieStore cookieStore, WXLoginParamModel wxLoginParamModel) {
        String url = "https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsg?lang=zh_CN&pass_ticket=" +
                wxLoginParamModel.getPass_ticket();
        Map<String, Object> requestParam = Maps.newHashMap();
        Map<String, Object> param = null;
        Map<String, String> baseRequest = Maps.newHashMap();
        baseRequest = setBaseRequest(wxLoginParamModel, baseRequest);
        requestParam.put("BaseRequest", baseRequest);
        param = Maps.newHashMap();
        String clientMsgId = ((Long) ((new Date().getTime()) << 4)).toString() + (((Double) (Math.random() * 10000)).longValue());
        param.put("ClientMsgId", clientMsgId);
        param.put("Content", content);
        param.put("FromUserName", selfUserModel.getUserName());
        param.put("LocalID", clientMsgId);
        param.put("ToUserName", wxUserModel.getUserName());
        param.put("Type", 1);
        requestParam.put("Msg", param);
        requestParam.put("Scene", 0);
        String result = HttpUtil.postJsonWithCookies(url, new Gson().toJson(requestParam),
                null, cookieStore);
        return result;
    }

    /**
     * 获取基本请求参数,返回参数形如下
     * {
     * "DeviceID":""，//e+15位随机数，
     * "Sid":"",
     * "Skey":"",
     * "Uin":""
     * }
     */
    public Map<String, String> setBaseRequest(WXLoginParamModel wxLoginParamModel, Map<String, String> baseRequest) {
        baseRequest = Maps.newHashMap();
        String DeviceID = "e" + ((Double) (Math.random() * (1000000000000000L))).longValue();

        baseRequest.put("DeviceID", DeviceID);
        baseRequest.put("Sid", wxLoginParamModel.getWxsid());
        baseRequest.put("Skey", wxLoginParamModel.getSkey());
        baseRequest.put("Uin", wxLoginParamModel.getWxuin());
        return baseRequest;
    }

}
