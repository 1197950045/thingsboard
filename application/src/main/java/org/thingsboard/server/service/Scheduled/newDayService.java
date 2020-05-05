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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.rule.engine.api.msg.DeviceAttributesEventNotificationMsg;
import org.thingsboard.server.actors.service.ActorService;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.kv.*;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.cluster.SendToClusterMsg;
import org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg;
import org.thingsboard.server.dao.attributes.CassandraBaseAttributesDao;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.dao.device.DeviceDao;
import org.thingsboard.server.dao.tenant.TenantDao;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
    private CassandraBaseAttributesDao attributesDao;

    @Autowired
    private TelemetrySubscriptionService tsSubService;

    @Autowired
    private ActorService actorService;

    @Autowired
    protected AuditLogService auditLogService;

    private static final ObjectMapper json = new ObjectMapper();

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }

    @Bean(destroyMethod="shutdown")
    public Executor taskExecutor() {
        return Executors.newScheduledThreadPool(1); //指定线程池大小
    }

    //3.添加定时任务
    @Scheduled(cron = "0 5 0 * * ?")
    @Transactional
    //或直接指定时间间隔，例如：5秒
    //@Scheduled(fixedRate=5000)
    public void configureTasks() throws ExecutionException, InterruptedException {
        TextPageLink pageLink = createPageLink(1000, null, null,null );
        List<Tenant> tenant = tenantDao.findTenantsByRegion(new TenantId(EntityId.NULL_UUID), "Global", pageLink);

        long ts = System.currentTimeMillis();
        for (Tenant t : tenant) {
            List<Device> devices = deviceDao.find(t.getTenantId());
            for (Device d : devices){
                EntityId entityId =EntityIdFactory.getByTypeAndUuid(EntityType.DEVICE, d.getId().getId());
                List<AttributeKvEntry> attributes =new ArrayList<>();
                attributes.add( new BaseAttributeKvEntry(new BooleanDataEntry("newDay",true),ts));
                String scope ="SERVER_SCOPE";
                User user = new User();
                user.setTenantId(t.getTenantId());
                tsSubService.saveAndNotify(t.getTenantId(),entityId,scope,attributes ,new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void tmp) {
                        logAttributesUpdated(user, entityId, scope, attributes, null);
                        if (entityId.getEntityType() == EntityType.DEVICE) {
                            DeviceId deviceId = new DeviceId(entityId.getId());
                            DeviceAttributesEventNotificationMsg notificationMsg = DeviceAttributesEventNotificationMsg.onUpdate(
                                    user.getTenantId(), deviceId, scope, attributes);
                            actorService.onMsg(new SendToClusterMsg(deviceId, notificationMsg));
                        }
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        logAttributesUpdated(user, entityId, scope, attributes, t);
                    }
                });
            }
        }


        System.out.println(LocalDateTime.now()+" [Scheduled] INFO o.t.s.s.s.newDayService - newday");
    }

    private void logAttributesUpdated(User user, EntityId entityId, String scope, List<AttributeKvEntry> attributes,  Throwable e) {
        try {
            logEntityAction(user, (UUIDBased & EntityId) entityId, null, null, ActionType.ATTRIBUTES_UPDATED, toException(e),
                    scope, attributes);
        } catch (ThingsboardException te) {
            log.warn("Failed to log attributes update", te);
        }
    }

    protected <E extends HasName, I extends EntityId> void logEntityAction(User user, I entityId, E entity, CustomerId customerId,
                                                                           ActionType actionType, Exception e,  Object... additionalInfo) throws ThingsboardException {
        if (customerId == null || customerId.isNullUid()) {
            customerId = user.getCustomerId();
        }
        if (e == null) {
            pushEntityActionToRuleEngine(entityId, entity, user, customerId, actionType,additionalInfo);
        }
    }

    private <E extends HasName, I extends EntityId> void pushEntityActionToRuleEngine(I entityId, E entity, User user, CustomerId customerId,
                                                                                      ActionType actionType, Object... additionalInfo) {
        String msgType = null;
        switch (actionType) {
            case ADDED:
                msgType = DataConstants.ENTITY_CREATED;
                break;
            case DELETED:
                msgType = DataConstants.ENTITY_DELETED;
                break;
            case UPDATED:
                msgType = DataConstants.ENTITY_UPDATED;
                break;
            case ASSIGNED_TO_CUSTOMER:
                msgType = DataConstants.ENTITY_ASSIGNED;
                break;
            case UNASSIGNED_FROM_CUSTOMER:
                msgType = DataConstants.ENTITY_UNASSIGNED;
                break;
            case ATTRIBUTES_UPDATED:
                msgType = DataConstants.ATTRIBUTES_UPDATED;
                break;
            case ATTRIBUTES_DELETED:
                msgType = DataConstants.ATTRIBUTES_DELETED;
                break;
            case ALARM_ACK:
                msgType = DataConstants.ALARM_ACK;
                break;
            case ALARM_CLEAR:
                msgType = DataConstants.ALARM_CLEAR;
                break;
        }
        if (!StringUtils.isEmpty(msgType)) {
            try {
                TbMsgMetaData metaData = new TbMsgMetaData();
                metaData.putValue("userId", user.getTenantId().toString());
                metaData.putValue("userName", user.getName());
                if (customerId != null && !customerId.isNullUid()) {
                    metaData.putValue("customerId", customerId.toString());
                }
                if (actionType == ActionType.ASSIGNED_TO_CUSTOMER) {
                    String strCustomerId = extractParameter(String.class, 1, additionalInfo);
                    String strCustomerName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("assignedCustomerId", strCustomerId);
                    metaData.putValue("assignedCustomerName", strCustomerName);
                } else if (actionType == ActionType.UNASSIGNED_FROM_CUSTOMER) {
                    String strCustomerId = extractParameter(String.class, 1, additionalInfo);
                    String strCustomerName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("unassignedCustomerId", strCustomerId);
                    metaData.putValue("unassignedCustomerName", strCustomerName);
                }
                ObjectNode entityNode;
                if (entity != null) {
                    entityNode = json.valueToTree(entity);
                    if (entityId.getEntityType() == EntityType.DASHBOARD) {
                        entityNode.put("configuration", "");
                    }
                } else {
                    entityNode = json.createObjectNode();
                    if (actionType == ActionType.ATTRIBUTES_UPDATED) {
                        String scope = extractParameter(String.class, 0, additionalInfo);
                        List<AttributeKvEntry> attributes = extractParameter(List.class, 1, additionalInfo);
                        metaData.putValue("scope", scope);
                        if (attributes != null) {
                            for (AttributeKvEntry attr : attributes) {
                                if (attr.getDataType() == DataType.BOOLEAN) {
                                    entityNode.put(attr.getKey(), attr.getBooleanValue().get());
                                } else if (attr.getDataType() == DataType.DOUBLE) {
                                    entityNode.put(attr.getKey(), attr.getDoubleValue().get());
                                } else if (attr.getDataType() == DataType.LONG) {
                                    entityNode.put(attr.getKey(), attr.getLongValue().get());
                                } else {
                                    entityNode.put(attr.getKey(), attr.getValueAsString());
                                }
                            }
                        }
                    } else if (actionType == ActionType.ATTRIBUTES_DELETED) {
                        String scope = extractParameter(String.class, 0, additionalInfo);
                        List<String> keys = extractParameter(List.class, 1, additionalInfo);
                        metaData.putValue("scope", scope);
                        ArrayNode attrsArrayNode = entityNode.putArray("attributes");
                        if (keys != null) {
                            keys.forEach(attrsArrayNode::add);
                        }
                    }
                }
                TbMsg tbMsg = new TbMsg(UUIDs.timeBased(), msgType, entityId, metaData, TbMsgDataType.JSON
                        , json.writeValueAsString(entityNode)
                        , null, null, 0L);
                actorService.onMsg(new SendToClusterMsg(entityId, new ServiceToRuleEngineMsg(user.getTenantId(), tbMsg)));
            } catch (Exception e) {
                log.warn("[{}] Failed to push entity action to rule engine: {}", entityId, actionType, e);
            }
        }
    }

    private <T> T extractParameter(Class<T> clazz, int index, Object... additionalInfo) {
        T result = null;
        if (additionalInfo != null && additionalInfo.length > index) {
            Object paramObject = additionalInfo[index];
            if (clazz.isInstance(paramObject)) {
                result = clazz.cast(paramObject);
            }
        }
        return result;
    }

    TextPageLink createPageLink(int limit, String textSearch, String idOffset, String textOffset) {
        UUID idOffsetUuid = null;
        if (StringUtils.isNotEmpty(idOffset)) {
            idOffsetUuid = UUID.fromString(idOffset);
        }
        return new TextPageLink(limit, textSearch, idOffsetUuid, textOffset);
    }
}
