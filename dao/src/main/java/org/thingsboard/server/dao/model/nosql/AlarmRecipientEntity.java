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
package org.thingsboard.server.dao.model.nosql;

import com.datastax.driver.core.utils.UUIDs;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.thingsboard.server.common.data.alarm.AlarmRecipient;
import org.thingsboard.server.common.data.alarm.AlarmRecipientId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseEntity;

import java.io.Serializable;
import java.util.UUID;


import static org.thingsboard.server.dao.model.ModelConstants.*;

@Table(name = ALARM_RECIPIENT_COLUMN_FAMILY_NAME)
@EqualsAndHashCode
@ToString
public final class AlarmRecipientEntity implements BaseEntity<AlarmRecipient>,Serializable {

    @Column(name = ID_PROPERTY)
    private UUID id;

    @PartitionKey(value = 0)
    @Column(name = ALARM_RECIPIENT_TENANT_ID_PROPERTY)
    private UUID tenantId;

    @PartitionKey(value = 1)
    @Column(name = ALARM_RECIPIENT_DEVICE_ID_PROPERTY)
    private UUID deviceId;

    @PartitionKey(value = 2)
    @Column(name = ALARM_RECIPIENT_CUSTOMER_ID_PROPERTY)
    private UUID customerId;

    @Column(name = ALARM_RECIPIENT_TYPE_PROPERTY)
    private String type;

    @PartitionKey(value = 3)
    @Column(name = ALARM_RECIPIENT_SEVERITY_PROPERTY)
    private String severity;

    @Column(name = ALARM_RECIPIENT_NAME_PROPERTY)
    private String name;

    @Column(name = ALARM_RECIPIENT_TELEPHONE_PROPERTY)
    private String telephone;

    @Column(name = ALARM_RECIPIENT_STATUS_PROPERTY)
    private int status;

    @Column(name = ALARM_RECIPIENT_EMAIL_PROPERTY)
    private String email;

    public AlarmRecipientEntity() {
        super();
    }

    public AlarmRecipientEntity(UUID id, UUID tenantId, UUID deviceId, UUID customerId, String type, String severity, String name, String telephone, int status, String email) {
        this.id = id;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.customerId = customerId;
        this.type = type;
        this.severity = severity;
        this.name = name;
        this.telephone = telephone;
        this.status = status;
        this.email = email;
    }

    public AlarmRecipientEntity(AlarmRecipient alarmRecipient) {
        if (alarmRecipient.getId() != null) {
            this.id = alarmRecipient.getId().getId();
        }
        if (alarmRecipient.getTenantId() != null) {
            this.tenantId = alarmRecipient.getTenantId().getId();
        }
        if (alarmRecipient.getDeviceId() != null) {
            this.deviceId = alarmRecipient.getDeviceId().getId();
        }
        if (alarmRecipient.getCustomerId() != null) {
            this.customerId = alarmRecipient.getCustomerId().getId();
        }
        this.type = alarmRecipient.getType();
        this.name = alarmRecipient.getName();
        this.telephone = alarmRecipient.getTelephone();
        this.severity = alarmRecipient.getSeverity();
        this.status=alarmRecipient.getStatus();
        this.email=alarmRecipient.getEmail();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public AlarmRecipient toData() {
        AlarmRecipient alarmRecipient = new AlarmRecipient(new AlarmRecipientId(id));
        alarmRecipient.setCreatedTime(UUIDs.unixTimestamp(id));
        if (tenantId != null) {
            alarmRecipient.setTenantId(new TenantId(tenantId));
        }
        if (deviceId != null) {
            alarmRecipient.setDeviceId(new DeviceId(deviceId));
        }
        if (customerId != null) {
            alarmRecipient.setCustomerId(new CustomerId(customerId));
        }
        alarmRecipient.setType(type);
        alarmRecipient.setSeverity(severity);
        alarmRecipient.setName(name);
        alarmRecipient.setTelephone(telephone);
        alarmRecipient.setStatus(status);
        alarmRecipient.setEmail(email);
        return alarmRecipient;
    }

}