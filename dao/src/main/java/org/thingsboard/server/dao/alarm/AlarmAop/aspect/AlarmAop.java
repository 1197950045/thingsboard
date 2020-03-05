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
package org.thingsboard.server.dao.alarm.AlarmAop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.dao.alarm.AlarmAop.annotation.CheckAlarm;
import org.thingsboard.server.dao.alarm.AlarmService;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class AlarmAop {
    @Autowired
    protected AlarmService alarmService ;
    private static final String critical = "CRITICAL ";
    private static final String major = "MAJOR";
    private static final String minor = "MINOR";
    private static final String waring = "WARNING";
    private static final String indeterminate = "INDETERMINATE";

    @Pointcut(value = "@annotation(checkAlarm)", argNames = "checkAlarm")
    public void pointcut(CheckAlarm checkAlarm) {
    }

    @Around(value = "pointcut(checkAlarm)", argNames = "joinPoint,checkAlarm")
    public Object  around(ProceedingJoinPoint joinPoint, CheckAlarm checkAlarm) throws Throwable {
        Object proceed = joinPoint.proceed();
        try{
        Map<String, Object> fieldsName = getFieldsName(joinPoint);
        TenantId tenantId = (TenantId) fieldsName.get("tenantId");
        EntityId entityId = (EntityId) fieldsName.get("entityId");
        TsKvEntry tsKvEntry = (TsKvEntry) fieldsName.get("tsKvEntry");
//        String rule ="{\"4b6b5df0-7bb7-11e9-bc9c-4dceec39225f\":{\"temperature\":{\"severity\":\"CRITICAL\",\"details\":\"温度\",\"propagate\":\"false\",\"type\":\"General Alarm\",\"max\":\"90\"},\"telemetry\":{\"severity\":\"WARNING\",\"details\":\"遥测\",\"propagate\":\"false\",\"type\":\"General Alarm\",\"min\":\"20\"}}}";
        String rule = ResolveJsonFileToString("Alarm.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode alarmRuleJson= mapper.readTree(rule);
        JsonNode alarmRule = alarmRuleJson.path(tenantId.getId().toString());
        if ( ! alarmRule.isNull() ){
            JsonNode condition = alarmRule.path(tsKvEntry.getKey());
            if ( ! condition.isNull()){
                if (alarmLimit(condition,Double.parseDouble(tsKvEntry.getValueAsString()))) {
                    String json = "{\"" + condition.get("details").asText() + "\":" + tsKvEntry.getValueAsString() + "}";
                    JsonNode details = mapper.readTree(json);
                    Alarm alarm = new Alarm();
                    alarm.setTenantId(tenantId);
                    alarm.setOriginator(entityId);
                    alarm.setSeverity(getSeverity(condition.get("severity").asText()));
                    alarm.setDetails(details);
                    alarm.setPropagate(condition.get("propagate").asBoolean());
                    alarm.setStatus(AlarmStatus.ACTIVE_UNACK);
                    alarm.setAckTs(0);
                    alarm.setStartTs(0);
                    alarm.setEndTs(0);
                    alarm.setClearTs(0);
                    alarm.setType(condition.get("type").asText());
                    checkNotNull(alarmService.createOrUpdateAlarm(alarm));
                }
            }
        }
        }catch (Exception e){
            log.error("Alarm rule is error:{}",e);
        }
        return proceed;
    }

    private Map<String, Object> getFieldsName(ProceedingJoinPoint joinPoint) {
        // 参数值
        Object[] args = joinPoint.getArgs();
        ParameterNameDiscoverer pnd = new DefaultParameterNameDiscoverer();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = pnd.getParameterNames(method);
        Map<String, Object> paramMap = new HashMap<>(32);
        if(null != parameterNames){
        for (int i = 0; i < parameterNames.length; i++) {
            paramMap.put(parameterNames[i], args[i]);
        }}
        return paramMap;
    }


    private  <T> List<T> castList(Object obj, Class<T> clazz) {
        List<T> result = new ArrayList<T>();
        if (obj instanceof List<?>) {
            for (Object o : (List<?>) obj) {
                result.add(clazz.cast(o));
            }
            return result;
        }
        return null;
    }

    private <T> T checkNotNull(T reference) throws ThingsboardException {
        if (reference == null) {
            throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        return reference;
    }

    private AlarmSeverity getSeverity(String alarmSeverity){
        AlarmSeverity severity = AlarmSeverity.CRITICAL;
        switch (alarmSeverity) {
            case major :
                severity = AlarmSeverity.MAJOR;
                return severity;
            case minor :
                severity = AlarmSeverity.MINOR;
                return severity;
            case indeterminate :
                severity = AlarmSeverity.INDETERMINATE;
                return severity;
            case waring:
                severity = AlarmSeverity.WARNING;
                return severity;
        }
        return severity;
    }

    private  boolean alarmLimit(JsonNode condition, double value){
        boolean alarm = false;
        JsonNode max = condition.get("max");
        JsonNode min = condition.get("min");
        JsonNode eqMax = condition.get("eqMax");
        JsonNode eqMin = condition.get("eqMin");
        if (null != max && value > max.asDouble()){
            alarm = true;
        }
        if (null != min && value < min.asDouble()){
                alarm = true;
        }
        if (null != eqMax && value >= eqMax.asDouble()){
                alarm = true;
        }
        if (null != eqMin && value <= eqMin.asDouble()){
                alarm = true;
        }
        return alarm;
    }

    public static String ResolveJsonFileToString(String filename){

        BufferedReader br = null;
        String result = null;
        try {

//            br = new BufferedReader(new InputStreamReader(getInputStream(path)));
            br = new BufferedReader(new InputStreamReader(getResFileStream(filename),"UTF-8"));
            StringBuffer message=new StringBuffer();
            String line = null;
            while((line = br.readLine()) != null) {
                message.append(line);
            }
            if (br != null) {
                br.close();
            }
            String defaultString=message.toString();
            result=defaultString.replace("\r\n", "").replaceAll(" +", "");
            log.info("result={}",result);

        } catch (IOException e) {
            try {
                ClassLoader classloader = Thread.currentThread().getContextClassLoader();
                InputStream in = classloader.getResourceAsStream(filename);
                br = new BufferedReader(new InputStreamReader(in,"UTF-8"));
                StringBuffer message=new StringBuffer();
                String line = null;
                while((line = br.readLine()) != null) {
                    message.append(line);
                }
                if (br != null) {
                    br.close();
                }
                if (in != null){
                    in.close();
                }
                String defaultString=message.toString();
                result=defaultString.replace("\r\n", "").replaceAll(" +", "");
                log.debug("for jar result={}",result);
            }catch (Exception e1){
                e1.printStackTrace();
            }

        }

        return result;
    }



    private static File getResFile(String filename) throws FileNotFoundException {
        File file = new File(filename);
        if (!file.exists()) { // 如果同级目录没有，则去config下面找
            log.debug("不在同级目录，进入config目录查找");
            file = new File("conf/"+filename);
        }
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) { //config目录下还是找不到，那就直接用classpath下的
            log.debug("不在config目录，进入classpath目录查找");
            file = ResourceUtils.getFile("classpath:"+filename);
        }
        return file;
    }

    /**
     *  通过文件名获取classpath路径下的文件流
     * @param
     * @return
     * @throws
     */
    private static FileInputStream getResFileStream(String filename) throws FileNotFoundException {
        FileInputStream fin = null;
        File file = getResFile(filename);
        log.info("getResFile path={}",file);
        fin = new FileInputStream(file);
        return fin;
    }

}
