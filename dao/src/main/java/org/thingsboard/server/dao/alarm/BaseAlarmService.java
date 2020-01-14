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
package org.thingsboard.server.dao.alarm;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.alarm.*;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntityRelationsQuery;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.relation.RelationsSearchParameters;
import org.thingsboard.server.dao.device.DeviceDao;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.tenant.TenantDao;
import org.thingsboard.server.dao.util.HttpClientUtil;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.text.SimpleDateFormat;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import static org.thingsboard.server.common.data.alarm.AlarmSeverity.CRITICAL;

import static org.thingsboard.server.dao.service.Validator.validateId;

@Service
@Slf4j
public class BaseAlarmService extends AbstractEntityService implements AlarmService {

    public static final String ALARM_RELATION_PREFIX = "ALARM_";

    //发件人邮箱
    private static String myEmailAccount = "alarm@jshlszjswwgc.onexmail.com";
    //密码
    private static String myEmailPassword = "Zuw5x2DHqHwyRk9K";
    // 发件人邮箱的 SMTP 服务器地址
    private static String myEmailSMTPHost = "smtp.qq.com";
    // 收件人邮箱
    private static String receiveMailAccount = "1197950045@qq.com";

    @Autowired
    private AlarmRecipientDao alarmRecipientDao;

    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private AlarmDao alarmDao;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private EntityService entityService;

    protected ExecutorService readResultsProcessingExecutor;

    @Autowired
    protected RelationService relationService;

    @PostConstruct
    public void startExecutor() {
        readResultsProcessingExecutor = Executors.newCachedThreadPool();
    }

    @PreDestroy
    public void stopExecutor() {
        if (readResultsProcessingExecutor != null) {
            readResultsProcessingExecutor.shutdownNow();
        }
    }

    @Override
    public Alarm createOrUpdateAlarm(Alarm alarm) {
        alarmDataValidator.validate(alarm, Alarm::getTenantId);
        try {
            if (alarm.getStartTs() == 0L) {
                alarm.setStartTs(System.currentTimeMillis());
            }
            if (alarm.getEndTs() == 0L) {
                alarm.setEndTs(alarm.getStartTs());
            }
            if (alarm.getId() == null) {
                Alarm existing = alarmDao.findLatestByOriginatorAndType(alarm.getTenantId(), alarm.getOriginator(), alarm.getType()).get();
                if (existing == null || existing.getStatus().isCleared()) {
                    return createAlarm(alarm);
                } else {
                    return updateAlarm(existing, alarm);
                }
            } else {
                return updateAlarm(alarm).get();
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ListenableFuture<Alarm> findLatestByOriginatorAndType(TenantId tenantId, EntityId originator, String type) {
        return alarmDao.findLatestByOriginatorAndType(tenantId, originator, type);
    }

    @Override
    public Boolean deleteAlarm(TenantId tenantId, AlarmId alarmId) {
        try {
            log.debug("Deleting Alarm Id: {}", alarmId);
            Alarm alarm = alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId()).get();
            if (alarm == null) {
                return false;
            }
            deleteEntityRelations(tenantId, alarm.getId());
            return alarmDao.deleteAlarm(tenantId, alarm);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }



    public Alarm createAlarm(Alarm alarm) throws InterruptedException, ExecutionException {
        log.debug("New Alarm : {}", alarm);
        Alarm saved = alarmDao.save(alarm.getTenantId(), alarm);
        createAlarmRelations(saved);
        Device device = deviceDao.findById(alarm.getTenantId(), alarm.getOriginator().getId());
        List<AlarmRecipient> alarmRecipients = alarmRecipientDao.findByTelephoneAndSeverity(alarm.getTenantId().getId(), alarm.getOriginator().getId(), device.getCustomerId().getId(), alarm.getSeverity().toString());
        /*for (AlarmRecipient alarmRecipient : alarmRecipients) {
            if (alarmRecipient.getStatus()==0){
                //发送短信
                sendNote(saved,device,alarmRecipient);
            }else if (alarmRecipient.getStatus()==1){
                //发送邮件
                sendEmail(saved,device,alarmRecipient);
            }else {
                //发送短信
                sendNote(saved,device,alarmRecipient);
                //发送邮件
                sendEmail(saved,device,alarmRecipient);
            }
        }*/

        return saved;
    }

    private void createAlarmRelations(Alarm alarm) throws InterruptedException, ExecutionException {
        if (alarm.isPropagate()) {
            List<EntityId> parentEntities = getParentEntities(alarm);
            for (EntityId parentId : parentEntities) {
                createAlarmRelation(alarm.getTenantId(), parentId, alarm.getId(), alarm.getStatus(), true);
            }
        }
        createAlarmRelation(alarm.getTenantId(), alarm.getOriginator(), alarm.getId(), alarm.getStatus(), true);
    }

    private List<EntityId> getParentEntities(Alarm alarm) throws InterruptedException, ExecutionException {
        EntityRelationsQuery query = new EntityRelationsQuery();
        query.setParameters(new RelationsSearchParameters(alarm.getOriginator(), EntitySearchDirection.TO, Integer.MAX_VALUE, false));
        return relationService.findByQuery(alarm.getTenantId(), query).get().stream().map(EntityRelation::getFrom).collect(Collectors.toList());
    }

    private ListenableFuture<Alarm> updateAlarm(Alarm update) {
        alarmDataValidator.validate(update, Alarm::getTenantId);
        return getAndUpdate(update.getTenantId(), update.getId(), new Function<Alarm, Alarm>() {
            @Nullable
            @Override
            public Alarm apply(@Nullable Alarm alarm) {
                if (alarm == null) {
                    return null;
                } else {
                    return updateAlarm(alarm, update);
                }
            }
        });
    }

    private Alarm updateAlarm(Alarm oldAlarm, Alarm newAlarm) {
        AlarmStatus oldStatus = oldAlarm.getStatus();
        AlarmStatus newStatus = newAlarm.getStatus();
        boolean oldPropagate = oldAlarm.isPropagate();
        boolean newPropagate = newAlarm.isPropagate();
        Alarm result = alarmDao.save(newAlarm.getTenantId(), merge(oldAlarm, newAlarm));
        if (!oldPropagate && newPropagate) {
            try {
                createAlarmRelations(result);
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Failed to update alarm relations [{}]", result, e);
                throw new RuntimeException(e);
            }
        } else if (oldStatus != newStatus) {
            updateRelations(oldAlarm, oldStatus, newStatus);
        }
        return result;
    }

    @Override
    public ListenableFuture<Boolean> ackAlarm(TenantId tenantId, AlarmId alarmId, long ackTime) {
        return getAndUpdate(tenantId, alarmId, new Function<Alarm, Boolean>() {
            @Nullable
            @Override
            public Boolean apply(@Nullable Alarm alarm) {
                if (alarm == null || alarm.getStatus().isAck()) {
                    return false;
                } else {
                    AlarmStatus oldStatus = alarm.getStatus();
                    AlarmStatus newStatus = oldStatus.isCleared() ? AlarmStatus.CLEARED_ACK : AlarmStatus.ACTIVE_ACK;
                    alarm.setStatus(newStatus);
                    alarm.setAckTs(ackTime);
                    alarmDao.save(alarm.getTenantId(), alarm);
                    updateRelations(alarm, oldStatus, newStatus);
                    return true;
                }
            }
        });
    }

    @Override
    public ListenableFuture<Boolean> clearAlarm(TenantId tenantId, AlarmId alarmId, JsonNode details, long clearTime) {
        return getAndUpdate(tenantId, alarmId, new Function<Alarm, Boolean>() {
            @Nullable
            @Override
            public Boolean apply(@Nullable Alarm alarm) {
                if (alarm == null || alarm.getStatus().isCleared()) {
                    return false;
                } else {
                    AlarmStatus oldStatus = alarm.getStatus();
                    AlarmStatus newStatus = oldStatus.isAck() ? AlarmStatus.CLEARED_ACK : AlarmStatus.CLEARED_UNACK;
                    alarm.setStatus(newStatus);
                    alarm.setClearTs(clearTime);
                    if (details != null) {
                        alarm.setDetails(details);
                    }
                    alarmDao.save(alarm.getTenantId(), alarm);
                    updateRelations(alarm, oldStatus, newStatus);
                    return true;
                }
            }
        });
    }

    @Override
    public ListenableFuture<Alarm> findAlarmByIdAsync(TenantId tenantId, AlarmId alarmId) {
        log.trace("Executing findAlarmById [{}]", alarmId);
        validateId(alarmId, "Incorrect alarmId " + alarmId);
        return alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId());
    }

    @Override
    public ListenableFuture<AlarmInfo> findAlarmInfoByIdAsync(TenantId tenantId, AlarmId alarmId) {
        log.trace("Executing findAlarmInfoByIdAsync [{}]", alarmId);
        validateId(alarmId, "Incorrect alarmId " + alarmId);
        return Futures.transformAsync(alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId()),
                a -> {
                    AlarmInfo alarmInfo = new AlarmInfo(a);
                    return Futures.transform(
                            entityService.fetchEntityNameAsync(tenantId, alarmInfo.getOriginator()), originatorName -> {
                                alarmInfo.setOriginatorName(originatorName);
                                return alarmInfo;
                            }
                    );
                });
    }

    @Override
    public ListenableFuture<TimePageData<AlarmInfo>> findAlarms(TenantId tenantId, AlarmQuery query) {
        ListenableFuture<List<AlarmInfo>> alarms = alarmDao.findAlarms(tenantId, query);
        if (query.getFetchOriginator() != null && query.getFetchOriginator().booleanValue()) {
            alarms = Futures.transformAsync(alarms, input -> {
                List<ListenableFuture<AlarmInfo>> alarmFutures = new ArrayList<>(input.size());
                for (AlarmInfo alarmInfo : input) {
                    alarmFutures.add(Futures.transform(
                            entityService.fetchEntityNameAsync(tenantId, alarmInfo.getOriginator()), originatorName -> {
                                if (originatorName == null) {
                                    originatorName = "Deleted";
                                }
                                alarmInfo.setOriginatorName(originatorName);
                                return alarmInfo;
                            }
                    ));
                }
                return Futures.successfulAsList(alarmFutures);
            });
        }
        return Futures.transform(alarms, new Function<List<AlarmInfo>, TimePageData<AlarmInfo>>() {
            @Nullable
            @Override
            public TimePageData<AlarmInfo> apply(@Nullable List<AlarmInfo> alarms) {
                return new TimePageData<>(alarms, query.getPageLink());
            }
        });
    }

    @Override
    public List<TimePageData<AlarmInfo>> findCustomersAlarms(TenantId tenantId, EntityId entityId, TimePageLink pageLink, AlarmSearchStatus alarmSearchStatus, AlarmStatus alarmStatus, Boolean fetchOriginator, String role){
        List<TimePageData<AlarmInfo>> alarmList = new ArrayList<>();
        switch (role) {
            case "manufacturer":
                List<Map<String, List<Device>>> maDevices = relationService.findManufacturerDevice(tenantId, entityId, "Contains", parseRelationTypeGroup("COMMON", RelationTypeGroup.COMMON));
                maDevices.forEach(m -> {
                    for (String key : m.keySet()) {
                        List<Device> devicesList = m.get(key);
                        devicesList.forEach(d ->{
                            try {
                                alarmList.add(this.findAlarms(tenantId, new AlarmQuery( EntityIdFactory.getByTypeAndId("DEVICE", d.getId().toString()), pageLink, alarmSearchStatus, alarmStatus, fetchOriginator)).get());
                            } catch (InterruptedException | ExecutionException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                });
                break;
            case "capitalist":
                List<Map<String, List<Device>>> caDevices = relationService.findCapitalistDevices(tenantId, entityId, "Contains", parseRelationTypeGroup("COMMON", RelationTypeGroup.COMMON));
                caDevices.forEach(c -> {
                    for (String key : c.keySet()) {
                        List<Device> devicesList = c.get(key);
                        devicesList.forEach(d ->{
                            try {
                                alarmList.add(this.findAlarms(tenantId, new AlarmQuery( EntityIdFactory.getByTypeAndId("DEVICE", d.getId().toString()), pageLink, alarmSearchStatus, alarmStatus, fetchOriginator)).get());
                            } catch (InterruptedException | ExecutionException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                });
                break;
            default:
                List<Device> leDevices = relationService.findLesseeDevices(tenantId, entityId, "Contains", parseRelationTypeGroup("COMMON", RelationTypeGroup.COMMON));
                leDevices.forEach(l -> {
                    try {
                        alarmList.add(this.findAlarms(tenantId, new AlarmQuery( EntityIdFactory.getByTypeAndId("DEVICE", l.getId().toString()), pageLink, alarmSearchStatus, alarmStatus, fetchOriginator)).get());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                });
                break;
        }

        return alarmList;
    }

    @Override
    public AlarmSeverity findHighestAlarmSeverity(TenantId tenantId, EntityId entityId, AlarmSearchStatus alarmSearchStatus,
                                                  AlarmStatus alarmStatus) {
        TimePageLink nextPageLink = new TimePageLink(100);
        boolean hasNext = true;
        AlarmSeverity highestSeverity = null;
        AlarmQuery query;
        while (hasNext && AlarmSeverity.CRITICAL != highestSeverity) {
            query = new AlarmQuery(entityId, nextPageLink, alarmSearchStatus, alarmStatus, false);
            List<AlarmInfo> alarms;
            try {
                alarms = alarmDao.findAlarms(tenantId, query).get();
            } catch (ExecutionException | InterruptedException e) {
                log.warn("Failed to find highest alarm severity. EntityId: [{}], AlarmSearchStatus: [{}], AlarmStatus: [{}]",
                        entityId, alarmSearchStatus, alarmStatus);
                throw new RuntimeException(e);
            }
            hasNext = alarms.size() == nextPageLink.getLimit();
            if (hasNext) {
                nextPageLink = new TimePageData<>(alarms, nextPageLink).getNextPageLink();
            }
            AlarmSeverity severity = detectHighestSeverity(alarms);
            if (severity == null) {
                continue;
            }
            if (severity == AlarmSeverity.CRITICAL || highestSeverity == null) {
                highestSeverity = severity;
            } else {
                highestSeverity = highestSeverity.compareTo(severity) < 0 ? highestSeverity : severity;
            }
        }
        return highestSeverity;
    }

    private AlarmSeverity detectHighestSeverity(List<AlarmInfo> alarms) {
        if (!alarms.isEmpty()) {
            List<AlarmInfo> sorted = new ArrayList(alarms);
            sorted.sort(Comparator.comparing(Alarm::getSeverity));
            return sorted.get(0).getSeverity();
        } else {
            return null;
        }
    }

    private void deleteRelation(TenantId tenantId, EntityRelation alarmRelation) {
        log.debug("Deleting Alarm relation: {}", alarmRelation);
        relationService.deleteRelation(tenantId, alarmRelation);
    }

    private void createRelation(TenantId tenantId, EntityRelation alarmRelation) {
        log.debug("Creating Alarm relation: {}", alarmRelation);
        relationService.saveRelation(tenantId, alarmRelation);
    }

    private Alarm merge(Alarm existing, Alarm alarm) {
        if (alarm.getStartTs() > existing.getEndTs()) {
            existing.setEndTs(alarm.getStartTs());
        }
        if (alarm.getEndTs() > existing.getEndTs()) {
            existing.setEndTs(alarm.getEndTs());
        }
        if (alarm.getClearTs() > existing.getClearTs()) {
            existing.setClearTs(alarm.getClearTs());
        }
        if (alarm.getAckTs() > existing.getAckTs()) {
            existing.setAckTs(alarm.getAckTs());
        }
        existing.setStatus(alarm.getStatus());
        existing.setSeverity(alarm.getSeverity());
        existing.setDetails(alarm.getDetails());
        existing.setPropagate(existing.isPropagate() || alarm.isPropagate());
        return existing;
    }

    private void updateRelations(Alarm alarm, AlarmStatus oldStatus, AlarmStatus newStatus) {
        try {
            List<EntityRelation> relations = relationService.findByToAsync(alarm.getTenantId(), alarm.getId(), RelationTypeGroup.ALARM).get();
            Set<EntityId> parents = relations.stream().map(EntityRelation::getFrom).collect(Collectors.toSet());
            for (EntityId parentId : parents) {
                updateAlarmRelation(alarm.getTenantId(), parentId, alarm.getId(), oldStatus, newStatus);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.warn("[{}] Failed to update relations. Old status: [{}], New status: [{}]", alarm.getId(), oldStatus, newStatus);
            throw new RuntimeException(e);
        }
    }

    private void createAlarmRelation(TenantId tenantId, EntityId entityId, EntityId alarmId, AlarmStatus status, boolean createAnyRelation) {
        if (createAnyRelation) {
            createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + AlarmSearchStatus.ANY.name(), RelationTypeGroup.ALARM));
        }
        createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.name(), RelationTypeGroup.ALARM));
        createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getClearSearchStatus().name(), RelationTypeGroup.ALARM));
        createRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getAckSearchStatus().name(), RelationTypeGroup.ALARM));
    }

    private void deleteAlarmRelation(TenantId tenantId, EntityId entityId, EntityId alarmId, AlarmStatus status) {
        deleteRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.name(), RelationTypeGroup.ALARM));
        deleteRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getClearSearchStatus().name(), RelationTypeGroup.ALARM));
        deleteRelation(tenantId, new EntityRelation(entityId, alarmId, ALARM_RELATION_PREFIX + status.getAckSearchStatus().name(), RelationTypeGroup.ALARM));
    }

    private void updateAlarmRelation(TenantId tenantId, EntityId entityId, EntityId alarmId, AlarmStatus oldStatus, AlarmStatus newStatus) {
        deleteAlarmRelation(tenantId, entityId, alarmId, oldStatus);
        createAlarmRelation(tenantId, entityId, alarmId, newStatus, false);
    }

    private <T> ListenableFuture<T> getAndUpdate(TenantId tenantId, AlarmId alarmId, Function<Alarm, T> function) {
        validateId(alarmId, "Alarm id should be specified!");
        ListenableFuture<Alarm> entity = alarmDao.findAlarmByIdAsync(tenantId, alarmId.getId());
        return Futures.transform(entity, function, readResultsProcessingExecutor);
    }

    private RelationTypeGroup parseRelationTypeGroup(String strRelationTypeGroup, RelationTypeGroup defaultValue) {
        RelationTypeGroup result = defaultValue;
        if (strRelationTypeGroup != null && strRelationTypeGroup.trim().length() > 0) {
            try {
                result = RelationTypeGroup.valueOf(strRelationTypeGroup);
            } catch (IllegalArgumentException e) {
            }
        }
        return result;
    }

    //发送短信
    public void sendNote(Alarm alarm,Device device,AlarmRecipient alarmRecipient){
        //进行短信发送
        //设置日期格式
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //查询customerId(未用)
        //UUID customerId = deviceDao.findByCustomerId(alarm.getTenantId().getId(), alarm.getOriginator().getId());
        //查询设备
        //Device device = deviceDao.findById(alarm.getTenantId(), alarm.getOriginator().getId());
        try {
            //查询告警接受者(多个)
            //List<AlarmRecipient> alarmRecipients = alarmRecipientDao.findByTelephoneAndSeverity(alarm.getTenantId().getId(), alarm.getOriginator().getId(), device.getCustomerId().getId(), alarm.getSeverity().toString());
            //信息替换(英文换成中文)
            /*String param ="";
            if (alarmRecipient.getSeverity().equals("CRITICAL")){
                param = device.getName()+","+df.format(alarm.getStartTs())+","+alarmRecipient.getType()+","+"危险";
            }else if (alarmRecipient.getSeverity().equals("MAJOR")){
                param = device.getName()+","+df.format(alarm.getStartTs())+","+alarmRecipient.getType()+","+"重要";
            }else if (alarmRecipient.getSeverity().equals("MINOR")){
                param = device.getName()+","+df.format(alarm.getStartTs())+","+alarmRecipient.getType()+","+"次要";
            }else if (alarmRecipient.getSeverity().equals("WARNING")){
                param = device.getName()+","+df.format(alarm.getStartTs())+","+alarmRecipient.getType()+","+"警告";
            }else if (alarmRecipient.getSeverity().equals("INDETERMINATE")){
                param = device.getName()+","+df.format(alarm.getStartTs())+","+alarmRecipient.getType()+","+"不确定";
            }*/
            String param =device.getName()+","+df.format(alarm.getStartTs())+","+alarmRecipient.getType()+","+transForm(alarmRecipient.getSeverity());
            //获取手机号
            String mobile = alarmRecipient.getTelephone();
            String uid = "";
            //使用工具类进行发送信息
            HttpClientUtil httpClientUtil = new HttpClientUtil();
            httpClientUtil.sendSms(param, mobile, uid);
        }catch (Exception e){
            System.out.println(e);
        }
    }

    //英文转换为中文
    public String transForm(String severity){
        String chinese="";
        if (severity.equals("CRITICAL")){
            chinese ="危险";
        }else if (severity.equals("MAJOR")){
            chinese ="重要";
        }else if (severity.equals("MINOR")){
            chinese ="次要";
        }else if (severity.equals("WARNING")){
            chinese ="警告";
        }else if (severity.equals("INDETERMINATE")){
            chinese ="不确定";
        }
        return chinese;
    }

    //发送邮件
    public void sendEmail(Alarm alarm,Device device,AlarmRecipient alarmRecipient){
        //创建参数配置, 用于连接邮件服务器的参数配置
        Properties props = new Properties();                    // 参数配置
        props.setProperty("mail.transport.protocol", "smtp");   // 使用的协议（JavaMail规范要求）
        props.setProperty("mail.smtp.host", myEmailSMTPHost);   // 发件人的邮箱的 SMTP 服务器地址
        props.setProperty("mail.smtp.auth", "true");

        //根据配置创建会话对象, 用于和邮件服务器交互
        Session session = Session.getInstance(props);
        session.setDebug(true);                                 // 设置为debug模式, 可以查看详细的发送 log

        try {
            System.out.println("alarmRecipient="+alarmRecipient);
            System.out.println("name="+alarmRecipient.getName());
            String transForm = transForm(alarmRecipient.getSeverity());
            //设置日期格式
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            //创建一封邮件
            MimeMessage message = new MimeMessage(session);
            // From: 发件人
            message.setFrom(new InternetAddress(myEmailAccount, "尤云", "UTF-8"));
            //To: 收件人
            message.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(alarmRecipient.getEmail(), "告警接收人", "UTF-8"));
            //Subject: 邮件主题
            message.setSubject(device.getName()+"触发告警", "UTF-8");
            //Content: 邮件正文
            message.setContent("尊敬的"+alarmRecipient.getName()+"用户您好,您的设备"+device.getName()+" 于 "+df.format(alarm.getStartTs())+" 触发 "+alarmRecipient.getType()+" 告警,告警的级别为 "+transForm+" ,请及时处理。", "text/html;charset=UTF-8");
            //设置发件时间
            message.setSentDate(new Date());
            //保存设置
            message.saveChanges();
            //发送短信
            createMimeMessage(session,message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //发送邮件
    public static void createMimeMessage(Session session,MimeMessage message){

        try {
            //根据 Session 获取邮件传输对象
            Transport transport = session.getTransport();
            //使用 邮箱账号 和 密码 连接邮件服务器, 这里认证的邮箱必须与 message 中的发件人邮箱一致, 否则报错
            transport.connect(myEmailAccount, myEmailPassword);
            //发送邮件, 发到所有的收件地址, message.getAllRecipients() 获取到的是在创建邮件对象时添加的所有收件人, 抄送人, 密送人
            transport.sendMessage(message, message.getAllRecipients());
            //关闭连接
            transport.close();
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private DataValidator<Alarm> alarmDataValidator =
            new DataValidator<Alarm>() {

                @Override
                protected void validateDataImpl(TenantId tenantId, Alarm alarm) {
                    if (StringUtils.isEmpty(alarm.getType())) {
                        throw new DataValidationException("Alarm type should be specified!");
                    }
                    if (alarm.getOriginator() == null) {
                        throw new DataValidationException("Alarm originator should be specified!");
                    }
                    if (alarm.getSeverity() == null) {
                        throw new DataValidationException("Alarm severity should be specified!");
                    }
                    if (alarm.getStatus() == null) {
                        throw new DataValidationException("Alarm status should be specified!");
                    }
                    if (alarm.getTenantId() == null) {
                        throw new DataValidationException("Alarm should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(alarm.getTenantId(), alarm.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Alarm is referencing to non-existent tenant!");
                        }
                    }
                }
            };
}
