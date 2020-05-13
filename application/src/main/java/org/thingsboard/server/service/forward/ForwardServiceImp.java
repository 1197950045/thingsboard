package org.thingsboard.server.service.forward;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.dao.forward.ForwardService;

import java.util.Date;
import java.util.UUID;

@Service
public class ForwardServiceImp  implements ForwardService {
    final String accessTokenUrl = "https://openapi.lechange.cn:443/openapi/accessToken";
    final String getKitTokenUrl = "https://openapi.lechange.cn:443/openapi/getKitToken";
    public String getaccessToken(JSONObject param){
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
        headers.set("Connection", "close");
        String nonce = UUID.randomUUID().toString();
        Long time = new Date().getTime()/1000;
        String sign =DigestUtils.md5Hex("time:" + time + ",nonce:" + nonce +
                ",appSecret:" + param.getString("appSecret"));
        JSONObject system = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject params = new JSONObject();
        String accessToken = "";

        system.put("ver",param.getString("ver"));
        system.put("sign",sign);
        system.put("appId",param.getString("appId"));
        system.put("time",time.toString());
        system.put("nonce",nonce);
        data.put("system",system);
        data.put("params",null);
        data.put("id",UUID.randomUUID().toString());

        HttpEntity<String> requestEntity = new HttpEntity<>(data.toJSONString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(accessTokenUrl, HttpMethod.POST,requestEntity,String.class);
        JSONObject rejson = new JSONObject();
        if(response.hasBody()) {
            System.err.println(response.getBody());
            rejson = JSONObject.parseObject(response.getBody());
            accessToken = rejson.getJSONObject("result").getJSONObject("data").getString("accessToken");
        }
        nonce = UUID.randomUUID().toString();
        sign =DigestUtils.md5Hex("time:" + time + ",nonce:" + nonce +
                ",appSecret:" + param.getString("appSecret"));
        system.put("sign",sign);
        system.put("nonce",nonce);
        params.put("token",accessToken);
        params.put("deviceId",param.getString("deviceId"));
        params.put("channelId",param.getString("channelId"));
        params.put("type",param.getString("type"));
        data.put("system",system);
        data.put("params",params);
        data.put("id",UUID.randomUUID().toString());
        requestEntity = new HttpEntity<>(data.toJSONString(), headers);
        response = restTemplate.exchange(getKitTokenUrl, HttpMethod.POST,requestEntity,String.class);
        if(response.hasBody()) {
            rejson = JSONObject.parseObject(response.getBody());
            if(rejson.getJSONObject("result").equals("操作成功")){
                return rejson.getJSONObject("result").getJSONObject("data").getString("kitToken");
            }
        }
        return response.getBody();
    }
}

