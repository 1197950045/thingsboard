package org.thingsboard.server.common.data.alarm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;

@Data
@Builder
@AllArgsConstructor
public class AlarmRecipient extends BaseData<AlarmRecipientId> implements HasName, HasTenantId {

    private AlarmRecipientId id;
    private TenantId tenantId;
    private DeviceId deviceId;
    private CustomerId customerId;
    private String type;
    private String name;
    private String telephone;
    private String severity;
    private String email;
    private int status;

    public AlarmRecipient() {
        super();
    }
    public AlarmRecipient(AlarmRecipientId id) {
        super(id);
    }

    public AlarmRecipient(AlarmRecipient alarmRecipient) {
        /*super(alarmRecipient);*/
        this.tenantId = alarmRecipient.getTenantId();
        this.deviceId = alarmRecipient.getDeviceId();
        this.customerId = alarmRecipient.getCustomerId();
        this.type = alarmRecipient.getType();
        this.name = alarmRecipient.getName();
        this.telephone = alarmRecipient.getTelephone();
        this.severity = alarmRecipient.getSeverity();
        this.status=alarmRecipient.getStatus();
        this.email=alarmRecipient.getEmail();
        this.id=alarmRecipient.getId();
    }

    @Override
    public String toString() {
        return "AlarmRecipient{" +
                "id=" + id +
                ",tenantId=" + tenantId +
                ", deviceId=" + deviceId +
                ", customerId=" + customerId +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", telephone='" + telephone + '\'' +
                ", severity='" + severity + '\'' +
                ", email='" + email + '\'' +
                ", status=" + status +
                '}';
    }
}
