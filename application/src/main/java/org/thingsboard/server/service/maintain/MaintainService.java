//package org.thingsboard.server.service.maintain;
//
//import com.alibaba.fastjson.JSONArray;
//import com.alibaba.fastjson.JSONObject;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.datastax.driver.core.utils.UUIDs;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ArrayNode;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import com.google.common.util.concurrent.FutureCallback;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.scheduling.annotation.SchedulingConfigurer;
//import org.springframework.scheduling.config.ScheduledTaskRegistrar;
//import org.springframework.transaction.annotation.Transactional;
//import org.thingsboard.server.actors.service.ActorService;
//import org.thingsboard.server.common.data.*;
//import org.thingsboard.server.common.data.audit.ActionType;
//import org.thingsboard.server.common.data.exception.ThingsboardException;
//import org.thingsboard.server.common.data.id.CustomerId;
//import org.thingsboard.server.common.data.id.EntityId;
//import org.thingsboard.server.common.data.id.EntityIdFactory;
//import org.thingsboard.server.common.data.id.UUIDBased;
//import org.thingsboard.server.common.data.kv.*;
//import org.thingsboard.server.common.msg.TbMsg;
//import org.thingsboard.server.common.msg.TbMsgDataType;
//import org.thingsboard.server.common.msg.TbMsgMetaData;
//import org.thingsboard.server.common.msg.cluster.SendToClusterMsg;
//import org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg;
//import org.thingsboard.server.dao.attributes.CassandraBaseAttributesDao;
//import org.thingsboard.server.dao.audit.AuditLogService;
//import org.thingsboard.server.dao.customer.CustomerDao;
//import org.thingsboard.server.service.security.model.SecurityUser;
//import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;
//
//import javax.annotation.Nullable;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
//
//import static org.thingsboard.server.controller.BaseController.toException;
//
//@Slf4j
//@Configuration      //1.主要用于标记配置类，兼备Component的效果。
//@EnableScheduling   // 2.开启定时任务
//@ComponentScan("org.thingsboard")
//public class MaintainService implements SchedulingConfigurer {
//    @Autowired
//    private CustomerDao customerDao;
//
//    @Autowired
//    private CassandraBaseAttributesDao attributesDao;
//
//    @Autowired
//    private TelemetrySubscriptionService tsSubService;
//
//    @Autowired
//    private ActorService actorService;
//
//    @Autowired
//    protected AuditLogService auditLogService;
//
//    private static final ObjectMapper json = new ObjectMapper();
//
//    @Override
//    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
//        taskRegistrar.setScheduler(taskExecutor());
//    }
//
//    @Bean(destroyMethod="shutdown")
//    public Executor taskExecutor() {
//        return Executors.newScheduledThreadPool(1); //指定线程池大小
//    }
//
//    //3.添加定时任务
//    @Scheduled(cron = "0/30 * * * * ?")
//    @Transactional
//    //或直接指定时间间隔，例如：5秒
//    //@Scheduled(fixedRate=5000)
//    public void configureTasks() throws ExecutionException, InterruptedException {
//        List<Customer> customers = customerDao.findCustomersAll();
//        JSONObject maintainJson;
//        long ts = System.currentTimeMillis();
//        System.err.println(LocalDateTime.now());
//        for (Customer c : customers) {
//            EntityId entityId =EntityIdFactory.getByTypeAndUuid("CUSTOMER", c.getId().getId());
//            Optional<AttributeKvEntry> maintains= attributesDao.find(c.getTenantId(),entityId,"SERVER_SCOPE","device_maintain").get();
//            if(maintains != null && maintains.isPresent()){
//                maintainJson = JSONArray.parseArray(maintains.get().getValue().toString()).getJSONObject(0);
//                for(Map.Entry<String, Object> entry : maintainJson.entrySet()) {
//                   Optional<AttributeKvEntry> maintain = attributesDao.find(c.getTenantId(),entityId,"SERVER_SCOPE",entry.getKey()).get();
//                    if(maintain != null && maintain.isPresent()){
//                        long value = Long.parseLong(maintain.get().getValue().toString())-1;
//                        attributesDao.save(c.getTenantId(), entityId, "SERVER_SCOPE", new BaseAttributeKvEntry(new LongDataEntry(entry.getKey(),value), ts));
//                    }else {
//                        attributesDao.save(c.getTenantId(), entityId, "SERVER_SCOPE", new BaseAttributeKvEntry(new LongDataEntry(entry.getKey(), Long.parseLong(entry.getValue().toString())), ts));
//                    }
//                }
//            }else {
//                attributesDao.save(c.getTenantId(), entityId, "SERVER_SCOPE", new BaseAttributeKvEntry(new StringDataEntry("device_maintain","[{}]"), ts));
//            }
//
//            List<AttributeKvEntry> attributes =new ArrayList<>();
//            attributes.add( new BaseAttributeKvEntry(new BooleanDataEntry("NewDay",false),ts));
//            tsSubService.saveAndNotify(c.getTenantId(), entityId, "SERVER_SCOPE",attributes, new FutureCallback<Void>() {
//                @Override
//                public void onSuccess(@Nullable Void tmp) {
//                    User user = new User();
//                    user.setCustomerId(c.getId());
//                    user.setTenantId(c.getTenantId());
//                    logAttributesUpdated(user, entityId, "SERVER_SCOPE", attributes,false, null);
//                }
//
//                @Override
//                public void onFailure(Throwable t) {
//                }
//            });
//        }
//
//
//        System.err.println("执行静态定时任务时间: " + LocalDateTime.now());
//    }
//
//    private void logAttributesUpdated(User user, EntityId entityId, String scope, List<AttributeKvEntry> attributes, boolean isMaintain,  Throwable e) {
//        try {
//            logEntityAction(user, (UUIDBased & EntityId) entityId, null, null, ActionType.ATTRIBUTES_UPDATED, toException(e), isMaintain,
//                    scope, attributes);
//        } catch (ThingsboardException te) {
//            log.warn("Failed to log attributes update", te);
//        }
//    }
//
//    protected <E extends HasName, I extends EntityId> void logEntityAction(User user, I entityId, E entity, CustomerId customerId,
//                                                                           ActionType actionType, Exception e, boolean isMaintain, Object... additionalInfo) throws ThingsboardException {
//        if (customerId == null || customerId.isNullUid()) {
//            customerId = user.getCustomerId();
//        }
//        if (e == null) {
//            pushEntityActionToRuleEngine(entityId, entity, user, customerId, actionType,isMaintain,additionalInfo);
//        }
//    }
//
//    private <E extends HasName, I extends EntityId> void pushEntityActionToRuleEngine(I entityId, E entity, User user, CustomerId customerId,
//                                                                                      ActionType actionType, boolean isMaintain, Object... additionalInfo) {
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
//                metaData.putValue("userId", user.getId().toString());
//                metaData.putValue("userName", user.getName());
//                if(isMaintain){
//                    metaData.putValue("isMaintain","true");
//                }
//                if (customerId != null && !customerId.isNullUid()) {
//                    metaData.putValue("customerId", customerId.toString());
//                }
//                if (actionType == ActionType.ASSIGNED_TO_CUSTOMER) {
//                    String strCustomerId = extractParameter(String.class, 1, additionalInfo);
//                    String strCustomerName = extractParameter(String.class, 2, additionalInfo);
//                    metaData.putValue("assignedCustomerId", strCustomerId);
//                    metaData.putValue("assignedCustomerName", strCustomerName);
//                } else if (actionType == ActionType.UNASSIGNED_FROM_CUSTOMER) {
//                    String strCustomerId = extractParameter(String.class, 1, additionalInfo);
//                    String strCustomerName = extractParameter(String.class, 2, additionalInfo);
//                    metaData.putValue("unassignedCustomerId", strCustomerId);
//                    metaData.putValue("unassignedCustomerName", strCustomerName);
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
//                    } else if (actionType == ActionType.ATTRIBUTES_DELETED) {
//                        String scope = extractParameter(String.class, 0, additionalInfo);
//                        List<String> keys = extractParameter(List.class, 1, additionalInfo);
//                        metaData.putValue("scope", scope);
//                        ArrayNode attrsArrayNode = entityNode.putArray("attributes");
//                        if (keys != null) {
//                            keys.forEach(attrsArrayNode::add);
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
//
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
//}
