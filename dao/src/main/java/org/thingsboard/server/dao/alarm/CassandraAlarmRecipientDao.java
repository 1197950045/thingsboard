/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import com.datastax.driver.core.querybuilder.Select;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.alarm.AlarmRecipient;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.nosql.AlarmRecipientEntity;
import org.thingsboard.server.dao.nosql.CassandraAbstractModelDao;
import org.thingsboard.server.dao.relation.RelationDao;
import org.thingsboard.server.dao.util.NoSqlDao;

import java.util.List;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.thingsboard.server.dao.model.ModelConstants.*;

@Component
@Slf4j
public class CassandraAlarmRecipientDao extends CassandraAbstractModelDao<AlarmRecipientEntity, AlarmRecipient> implements AlarmRecipientDao {

    @Override
    protected Class<AlarmRecipientEntity> getColumnFamilyClass() {
        return null;
    }

    //设置表名
    @Override
    protected String getColumnFamilyName() {
        return ALARM_RECIPIENT_COLUMN_FAMILY_NAME;
    }


    //保存
    @Override
    public AlarmRecipient save(TenantId tenantId, AlarmRecipient domain) {
        log.debug("Save asset [{}] ", domain);
        return super.save(tenantId, domain);
    }

    @Override
    public List<AlarmRecipient> find(TenantId tenantId) {
        Select select = select().from(getColumnFamilyName()).allowFiltering();
        Select.Where query = select.where();
        query.and(eq(ALARM_RECIPIENT_TENANT_ID_PROPERTY, tenantId.getId()));
        return findListByTenantId(tenantId,query);
    }

    //查询全部(根据客户id)
    @Override
    public List<AlarmRecipient> findAllByCustomerId(TenantId tenantId, CustomerId customerId) {
        Select select = select().all().from(getColumnFamilyName()).allowFiltering();
        Select.Where query = select.where();
        query.and(eq(ALARM_RECIPIENT_TENANT_ID_PROPERTY, tenantId.getId()));
        query.and(eq(ALARM_RECIPIENT_CUSTOMER_ID_PROPERTY, customerId.getId()));
        return findListByTenantId(tenantId,query);
    }

    //查询详情(根据客户id)
    @Override
    public AlarmRecipient findByCustomerId(TenantId tenantId, CustomerId customerId, UUID id) {
        Select select = select().all().from(getColumnFamilyName()).allowFiltering();
        Select.Where query = select.where();
        query.and(eq(ALARM_RECIPIENT_TENANT_ID_PROPERTY, tenantId.getId()));
        query.and(eq(ALARM_RECIPIENT_CUSTOMER_ID_PROPERTY, customerId.getId()));
        query.and(eq(ID_PROPERTY, id));
        return findByTenantId(tenantId,query);
    }

    //删除
    @Override
    public boolean removeById(TenantId tenantId, UUID key) {
        return super.removeById(tenantId, key);
    }

    //查询手机号和状态
    @Override
    public List<AlarmRecipient> findByTelephoneAndSeverity(UUID tenantId, UUID deviceId, UUID customerId, String severity){
        log.debug("Try to find alarm_recipient by tenantId [{}] and deviceId [{}] and customerId [{}] and type [{}]", tenantId, deviceId,customerId,severity);
        Select select = select().from(getColumnFamilyName()).allowFiltering();
        Select.Where query = select.where();
        query.and(eq(ALARM_RECIPIENT_TENANT_ID_PROPERTY, tenantId));
        query.and(eq(ALARM_RECIPIENT_DEVICE_ID_PROPERTY, deviceId));
        query.and(eq(ALARM_RECIPIENT_CUSTOMER_ID_PROPERTY, customerId));
        query.and(eq(ALARM_RECIPIENT_SEVERITY_PROPERTY, severity));
        //query.orderBy(QueryBuilder.asc(ModelConstants.ALARM_RECIPIENT_TYPE_PROPERTY), QueryBuilder.desc(ModelConstants.ID_PROPERTY));
        return findListByTenantId(new TenantId(tenantId),query);
        /*List<AlarmRecipientEntity> listByStatement = findListByStatement(new TenantId(tenantId), query);
        System.out.println("listByStatement="+listByStatement);
        return DaoUtil.convertDataList(listByStatement);*/
    }

    @Override
    public AlarmRecipient findById(TenantId tenantId, UUID key) {
        log.debug("Get alarm by id {}", key);
        Select.Where query = select().from(getColumnFamilyName()).allowFiltering().where(eq(ModelConstants.ID_PROPERTY, key));
        query.limit(1);
        return findByTenantId(tenantId,query);
    }
}
