/**
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;

public class HttpClientUtil {
	
	private PoolingHttpClientConnectionManager cm = null;
	private static org.thingsboard.server.dao.util.HttpClientUtil HttpClientUtil;
	private String url="https://open.ucpaas.com/ol/sms/sendsms";
	private String sid="fffa7f76f8c3d8cead64dbdac04caac2";
	private String token="3aa6eff99daa34494ffb7bf269a5dbd4";
	private String appid="6046779bbc0d4535b4634ac2d71efa00";
	private String templateid="518752";

	public String sendSms(String param, String mobile,String uid) {

		String result = "";

		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("sid", sid);
			jsonObject.put("token", token);
			jsonObject.put("appid", appid);
			jsonObject.put("templateid", templateid);
			jsonObject.put("param", param);
			jsonObject.put("mobile", mobile);
			jsonObject.put("uid", uid);

			String body = jsonObject.toJSONString();

			System.out.println("body = " + body);

			result =postJson(url, body, "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public static String postJson(String url, String body, String charset) {
		
		String result = null;

			CloseableHttpClient httpClient = null;
			HttpPost httpPost = null;
			try {
				httpClient =getInstance().getHttpClient();
				httpPost = new HttpPost(url);
				
				// 设置连接超时,设置读取超时
				RequestConfig requestConfig = RequestConfig.custom()
						.setConnectTimeout(10000)	
		                .setSocketTimeout(10000)	
		                .build();
				httpPost.setConfig(requestConfig);
				
				httpPost.setHeader("Accept", "application/json");
				httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
				
				// 设置参数
				StringEntity se = new StringEntity(body, "UTF-8");
				httpPost.setEntity(se);
				HttpResponse response = httpClient.execute(httpPost);
				if (response != null) {
					HttpEntity resEntity = response.getEntity();
					if (resEntity != null) {
						result = EntityUtils.toString(resEntity, charset);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}

		return result;
	}
	public static org.thingsboard.server.dao.util.HttpClientUtil getInstance() {
		if (HttpClientUtil == null) {
			synchronized (org.thingsboard.server.dao.util.HttpClientUtil.class) {
				if (HttpClientUtil == null) {
					HttpClientUtil = new HttpClientUtil();
					HttpClientUtil.init();
				}
			}
		}
		return HttpClientUtil;
	}

	private void init() {
		LayeredConnectionSocketFactory sslsf = null;
		try {
			sslsf = new SSLConnectionSocketFactory(SSLContext.getDefault());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
				.register("https", sslsf).register("http", new PlainConnectionSocketFactory()).build();
		cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		cm.setMaxTotal(200);
		cm.setDefaultMaxPerRoute(20);
	}

	public CloseableHttpClient getHttpClient() {
		CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();

		return httpClient;
	}
}
