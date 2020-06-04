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

package org.thingsboard.server.service.Scheduled;
import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.rule.engine.api.msg.DeviceAttributesEventNotificationMsg;
import org.thingsboard.server.actors.service.ActorService;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.kv.*;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.cluster.SendToClusterMsg;
import org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg;
import org.thingsboard.server.common.transport.TransportContext;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;
import org.thingsboard.server.dao.attributes.CassandraBaseAttributesDao;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceDao;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.tenant.TenantDao;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;
import org.thingsboard.server.transport.http.DeviceApiController;
import org.thingsboard.server.transport.http.HttpTransportContext;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.thingsboard.server.controller.BaseController.toException;

@Slf4j
@Configuration      //1.主要用于标记配置类，兼备Component的效果。
@EnableScheduling   // 2.开启定时任务
@ComponentScan("org.thingsboard")
public class newDayService implements SchedulingConfigurer {
    @Autowired
    private TenantDao tenantDao;
    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private DeviceCredentialsService deviceCredentialsService;

//    @Autowired
//    private TelemetrySubscriptionService tsSubService;
//
//    @Autowired
//    private ActorService actorService;
//
//    @Autowired
//    private AuditLogService auditLogService;
//
//    @Autowired
//    private DeviceService deviceService;

    @Autowired
    private HttpTransportContext transportContext;

//    private static final ObjectMapper json = new ObjectMapper();

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }

    @Bean(destroyMethod="shutdown")
    public Executor taskExecutor() {
        return Executors.newScheduledThreadPool(1); //指定线程池大小
    }

    //3.添加定时任务
    @Scheduled(cron = "0 0 0 * * ?",zone = "Asia/Shanghai")
//    @Scheduled(cron = "30 * * * * ?",zone = "Asia/Shangha    i")
    @Transactional
    public void configureTasks() throws ExecutionException, InterruptedException {
        TextPageLink pageLink = createPageLink(1000, null, null,null );
        List<Tenant> tenant = tenantDao.findTenantsByRegion(new TenantId(EntityId.NULL_UUID), "Global", pageLink);


        long ts = System.currentTimeMillis();
        for (Tenant t : tenant) {
            List<Device> devices = deviceDao.find(t.getTenantId());
            for (Device d : devices){
                DeviceCredentials credential = deviceCredentialsService.findDeviceCredentialsByDeviceId(t.getTenantId(), d.getId());
                String deviceToken = credential.getCredentialsId();
//                EntityId entityId =EntityIdFactory.getByTypeAndUuid(EntityType.DEVICE, d.getId().getId());
//                List<AttributeKvEntry> attributes =new ArrayList<>();
//                attributes.add( new BaseAttributeKvEntry(new BooleanDataEntry("newDay",true),ts));
//                String scope ="SERVER_SCOPE";
//                User user = new User();
//                user.setTenantId(t.getTenantId());
//                tsSubService.saveAndNotify(t.getTenantId(),entityId,scope,attributes ,new FutureCallback<Void>() {
//                    @Override
//                    public void onSuccess(@Nullable Void tmp) {
//                        logAttributesUpdated(user, entityId, scope, attributes, null);
//                        if (entityId.getEntityType() == EntityType.DEVICE) {
//                            DeviceId deviceId = new DeviceId(entityId.getId());
//                            //走规则链
//                            DeviceAttributesEventNotificationMsg notificationMsg = DeviceAttributesEventNotificationMsg.onUpdate(
//                                    user.getTenantId(), deviceId, scope, attributes);
//                            actorService.onMsg(new SendToClusterMsg(deviceId, notificationMsg));
//                        }
//                    }
//                    @Override
//                    public void onFailure(Throwable t) {
//                        logAttributesUpdated(user, entityId, scope, attributes, t);
//                    }
//                });
                if(!deviceToken.isEmpty()){
                String json = "{\"newDay\":\"true\"}";
                DeferredResult<ResponseEntity> responseWriter = new DeferredResult<>();
                transportContext.getTransportService().process(TransportProtos.ValidateDeviceTokenRequestMsg.newBuilder().setToken(deviceToken).build(),
                        new DeviceAuthCallback(transportContext, responseWriter, sessionInfo -> {
                            TransportService transportService = transportContext.getTransportService();
                            transportService.process(sessionInfo, JsonConverter.convertToAttributesProto(new JsonParser().parse(json)),
                                    new HttpOkCallback(responseWriter));
                        }));
                }
            }
        }
        System.out.println(LocalDateTime.now()+" [Scheduled] INFO o.t.s.s.s.newDayService - newday");
    }

//    private void logAttributesUpdated(User user, EntityId entityId, String scope, List<AttributeKvEntry> attributes,  Throwable e) {
//        try {
//            logEntityAction(user, (UUIDBased & EntityId) entityId, null, null, ActionType.ATTRIBUTES_UPDATED, toException(e),
//                    scope, attributes);
//        } catch (ThingsboardException te) {
//            log.warn("Failed to log attributes update", te);
//        }
//    }
//
//    protected <E extends HasName, I extends EntityId> void logEntityAction(User user, I entityId, E entity, CustomerId customerId,
//                                                                           ActionType actionType, Exception e,  Object... additionalInfo) throws ThingsboardException {
//        if (customerId == null || customerId.isNullUid()) {
//            customerId = user.getCustomerId();
//        }
//        if (e == null) {
//            pushEntityActionToRuleEngine(entityId, entity, user, customerId, actionType,additionalInfo);
//        }
//    }
//
//    private <E extends HasName, I extends EntityId> void pushEntityActionToRuleEngine(I entityId, E entity, User user, CustomerId customerId,
//                                                                                      ActionType actionType, Object... additionalInfo) {
//        String msgType = null;
//        switch (actionType) {
//            case ADDED:
//                msgType = DataConstants.ENTITY_CREATED;
//                break;
//            case DELETED:
//                msgType = DataConstants.ENTITY_DELETED;
//                break;
//            case UPDATED:
//                msgType = DataConstants.ENTITY_UPDATED;
//                break;
//            case ASSIGNED_TO_CUSTOMER:
//                msgType = DataConstants.ENTITY_ASSIGNED;
//                break;
//            case UNASSIGNED_FROM_CUSTOMER:
//                msgType = DataConstants.ENTITY_UNASSIGNED;
//                break;
//            case ATTRIBUTES_UPDATED:
//                msgType = DataConstants.ATTRIBUTES_UPDATED;
//                break;
//            case ATTRIBUTES_DELETED:
//                msgType = DataConstants.ATTRIBUTES_DELETED;
//                break;
//            case ALARM_ACK:
//                msgType = DataConstants.ALARM_ACK;
//                break;
//            case ALARM_CLEAR:
//                msgType = DataConstants.ALARM_CLEAR;
//                break;
//        }
//        if (!StringUtils.isEmpty(msgType)) {
//            try {
//                TbMsgMetaData metaData = new TbMsgMetaData();
//                metaData.putValue("userId", user.getTenantId().toString());
//                metaData.putValue("userName", user.getName());
//                metaData.putValue("entityType", entityId.getEntityType().toString());
//                metaData.putValue("entityId", entityId.getId().toString());
//                if (customerId != null && !customerId.isNullUid()) {
//                    metaData.putValue("customerId", customerId.toString());
//                }
//                ObjectNode entityNode;
//                if (entity != null) {
//                    entityNode = json.valueToTree(entity);
//                    if (entityId.getEntityType() == EntityType.DASHBOARD) {
//                        entityNode.put("configuration", "");
//                    }
//                } else {
//                    entityNode = json.createObjectNode();
//                    if (actionType == ActionType.ATTRIBUTES_UPDATED) {
//                        String scope = extractParameter(String.class, 0, additionalInfo);
//                        List<AttributeKvEntry> attributes = extractParameter(List.class, 1, additionalInfo);
//                        metaData.putValue("scope", scope);
//                        if (attributes != null) {
//                            for (AttributeKvEntry attr : attributes) {
//                                if (attr.getDataType() == DataType.BOOLEAN) {
//                                    entityNode.put(attr.getKey(), attr.getBooleanValue().get());
//                                } else if (attr.getDataType() == DataType.DOUBLE) {
//                                    entityNode.put(attr.getKey(), attr.getDoubleValue().get());
//                                } else if (attr.getDataType() == DataType.LONG) {
//                                    entityNode.put(attr.getKey(), attr.getLongValue().get());
//                                } else {
//                                    entityNode.put(attr.getKey(), attr.getValueAsString());
//                                }
//                            }
//                        }
//                    }
//                }
//                TbMsg tbMsg = new TbMsg(UUIDs.timeBased(), msgType, entityId, metaData, TbMsgDataType.JSON
//                        , json.writeValueAsString(entityNode)
//                        , null, null, 0L);
//                actorService.onMsg(new SendToClusterMsg(entityId, new ServiceToRuleEngineMsg(user.getTenantId(), tbMsg)));
//            } catch (Exception e) {
//                log.warn("[{}] Failed to push entity action to rule engine: {}", entityId, actionType, e);
//            }
//        }
//    }

//    private <T> T extractParameter(Class<T> clazz, int index, Object... additionalInfo) {
//        T result = null;
//        if (additionalInfo != null && additionalInfo.length > index) {
//            Object paramObject = additionalInfo[index];
//            if (clazz.isInstance(paramObject)) {
//                result = clazz.cast(paramObject);
//            }
//        }
//        return result;
//    }

    private TextPageLink createPageLink(int limit, String textSearch, String idOffset, String textOffset) {
        UUID idOffsetUuid = null;
        if (StringUtils.isNotEmpty(idOffset)) {
            idOffsetUuid = UUID.fromString(idOffset);
        }
        return new TextPageLink(limit, textSearch, idOffsetUuid, textOffset);
    }

    private static class DeviceAuthCallback implements TransportServiceCallback<TransportProtos.ValidateDeviceCredentialsResponseMsg> {
        private final TransportContext transportContext;
        private final DeferredResult<ResponseEntity> responseWriter;
        private final Consumer<TransportProtos.SessionInfoProto> onSuccess;

        DeviceAuthCallback(TransportContext transportContext, DeferredResult<ResponseEntity> responseWriter, Consumer<TransportProtos.SessionInfoProto> onSuccess) {
            this.transportContext = transportContext;
            this.responseWriter = responseWriter;
            this.onSuccess = onSuccess;
        }

        @Override
        public void onSuccess(TransportProtos.ValidateDeviceCredentialsResponseMsg msg) {
            if (msg.hasDeviceInfo()) {
                UUID sessionId = UUID.randomUUID();
                TransportProtos.DeviceInfoProto deviceInfoProto = msg.getDeviceInfo();
                TransportProtos.SessionInfoProto sessionInfo = TransportProtos.SessionInfoProto.newBuilder()
                        .setNodeId(transportContext.getNodeId())
                        .setTenantIdMSB(deviceInfoProto.getTenantIdMSB())
                        .setTenantIdLSB(deviceInfoProto.getTenantIdLSB())
                        .setDeviceIdMSB(deviceInfoProto.getDeviceIdMSB())
                        .setDeviceIdLSB(deviceInfoProto.getDeviceIdLSB())
                        .setSessionIdMSB(sessionId.getMostSignificantBits())
                        .setSessionIdLSB(sessionId.getLeastSignificantBits())
                        .build();
                onSuccess.accept(sessionInfo);
            } else {
                responseWriter.setResult(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));
            }
        }

        @Override
        public void onError(Throwable e) {
            log.warn("Failed to process request", e);
            responseWriter.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private static class HttpOkCallback implements TransportServiceCallback<Void> {
        private final DeferredResult<ResponseEntity> responseWriter;

        private HttpOkCallback(DeferredResult<ResponseEntity> responseWriter) {
            this.responseWriter = responseWriter;
        }

        @Override
        public void onSuccess(Void msg) {
            responseWriter.setResult(new ResponseEntity<>(HttpStatus.OK));
        }

        @Override
        public void onError(Throwable e) {
            responseWriter.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
