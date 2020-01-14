package org.thingsboard.server.dao.alarm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.alarm.AlarmRecipient;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.entity.AbstractEntityService;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AlarmRecipientServiceImpl extends AbstractEntityService implements AlarmRecipientService {
    @Autowired
    private AlarmRecipientDao alarmRecipientDao;

    //查询手机号和状态
    @Override
    public List<AlarmRecipient> findByTelephoneAndSeverity(UUID tenantId, UUID deviceId, UUID customerId, String severity) {
        try {
            List<AlarmRecipient> alarmRecipientList = alarmRecipientDao.findByTelephoneAndSeverity(tenantId, deviceId, customerId, severity);
            return alarmRecipientList;
        } catch (Exception e) {
            e.printStackTrace();
        }
    return null;
    }

    //创建告警接受者
    @Override
    public AlarmRecipient createAlarmRecipient(AlarmRecipient alarmRecipient) {
        AlarmRecipient recipient = alarmRecipientDao.save(alarmRecipient.getTenantId(), alarmRecipient);
        return recipient;
    }

    //根据id查询
    @Override
    public AlarmRecipient findById(TenantId tenantId, UUID id) {
        return alarmRecipientDao.findById(tenantId,id);
    }

    //删除
    @Override
    public Boolean deleteAlarmRecipient(TenantId tenantId, UUID id) {
        log.debug("Deleting Alarm Id: {}", id);
        return alarmRecipientDao.removeById(tenantId,id);
    }

    //查询所有
    @Override
    public List<AlarmRecipient> find(TenantId tenantId) {
        return alarmRecipientDao.find(tenantId);
    }

    //查询全部(根据客户id)
    @Override
    public List<AlarmRecipient> findAllByCustomerId(TenantId tenantId, CustomerId customerId) {
        return alarmRecipientDao.findAllByCustomerId(tenantId,customerId);
    }

    //查询详情(根据客户id)
    @Override
    public AlarmRecipient findByCustomerId(TenantId tenantId, CustomerId customerId, UUID id) {
        return alarmRecipientDao.findByCustomerId(tenantId,customerId,id);
    }
}
