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
