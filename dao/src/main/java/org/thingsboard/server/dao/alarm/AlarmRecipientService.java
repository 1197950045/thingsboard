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

import org.thingsboard.server.common.data.alarm.AlarmRecipient;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.List;
import java.util.UUID;

public interface AlarmRecipientService {

    //查询手机号和状态
    List<AlarmRecipient> findByTelephoneAndSeverity(UUID tenantId, UUID deviceId, UUID customerId, String severity);

    //保存
    AlarmRecipient createAlarmRecipient(AlarmRecipient alarmRecipient);

    //根据id查询
    AlarmRecipient findById(TenantId tenantId, UUID id);

    //根据id删除
    Boolean deleteAlarmRecipient(TenantId tenantId, UUID id);

    //查询所有
    List<AlarmRecipient> find(TenantId tenantId);

    //查询全部(根据客户id)

    List<AlarmRecipient> findAllByCustomerId(TenantId tenantId, CustomerId customerId);

    //查询详情(根据客户id)
    AlarmRecipient findByCustomerId(TenantId tenantId, CustomerId customerId, UUID id);
}
