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
package org.thingsboard.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.common.data.alarm.AlarmRecipient;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.dao.alarm.AlarmRecipientService;
import org.thingsboard.server.dao.alarm.BaseAlarmService;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.security.permission.Resource;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/api")
public class AlarmRecipientController extends BaseController {

    @Autowired
    private AlarmRecipientService alarmRecipientService;

    @Autowired
    private BaseAlarmService baseAlarmService;

    public static final String AlarmRecipient_ID = "alarmRecipientId";

    public static final String Device_ID = "deviceId";

    private CustomerId Customer_Id=new CustomerId(EntityId.NULL_UUID);


    //保存告警接收者
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/createAlarmRecipient", method = RequestMethod.POST)
    public AlarmRecipient saveAlarmRecipient(@RequestBody AlarmRecipient alarmRecipient) throws ThingsboardException {
        try {
            //获取当前用户的TenantId
            alarmRecipient.setTenantId(getCurrentUser().getTenantId());
            alarmRecipient.setCustomerId(getCurrentUser().getCustomerId());
            //验证权限
            //accessControlService.checkPermission(getCurrentUser(), Resource.ALARM_RECIPIENT, Operation.CREATE, alarmRecipient.getId(), alarmRecipient);
            //检查是否为空
            //System.out.println(alarmRecipient);
            AlarmRecipient recipient = checkNotNull(alarmRecipientService.createAlarmRecipient(alarmRecipient));
            return null;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    //修改告警接收者信息
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/updateAlarmRecipient", method = RequestMethod.POST)
    @ResponseBody
    public AlarmRecipient updateAlarmRecipient(@RequestBody AlarmRecipient alarmRecipient) throws ThingsboardException {
        //检查权限
        accessControlService.checkPermission(getCurrentUser(), Resource.ALARM_RECIPIENT, Operation.WRITE, alarmRecipient.getId(), alarmRecipient);
        //检查是否为空
        AlarmRecipient recipient = checkNotNull(alarmRecipientService.findById(getCurrentUser().getTenantId(), alarmRecipient.getId().getId()));
        recipient.setName(alarmRecipient.getName());
        recipient.setTelephone(alarmRecipient.getTelephone());
        recipient.setSeverity(alarmRecipient.getSeverity());
        return alarmRecipientService.createAlarmRecipient(recipient);
    }

    //删除告警接受者
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarmRecipient/{alarmRecipientId}/delete", method = RequestMethod.DELETE)
    @ResponseBody
    public Boolean deleteAlarmRecipient(@PathVariable(AlarmRecipient_ID) String strAlarmRecipientId) throws ThingsboardException {
        //检验传递参数是否为空或者0
        checkParameter(AlarmRecipient_ID, strAlarmRecipientId);
        //转换为uuid
        UUID id = toUUID(strAlarmRecipientId);
        //检查是否为空
        AlarmRecipient alarmRecipient = checkNotNull(alarmRecipientService.findById(getCurrentUser().getTenantId(), id));
        //检查权限
        //accessControlService.checkPermission(getCurrentUser(), Resource.ALARM_RECIPIENT, Operation.WRITE, alarmRecipient.getId(), alarmRecipient);
        Boolean aBoolean = alarmRecipientService.deleteAlarmRecipient(alarmRecipient.getTenantId(), id);
        System.out.println(aBoolean);
        return alarmRecipientService.deleteAlarmRecipient(alarmRecipient.getTenantId(),id);
    }

    //根据id查询
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarmRecipient/{alarmRecipientId}/get", method = RequestMethod.GET)
    @ResponseBody
    public AlarmRecipient getAlarmRecipientById(@PathVariable(AlarmRecipient_ID) String strAlarmRecipientId) throws ThingsboardException {
        try {
            System.out.println("查询执行了");
            //检验传递参数是否为空或者0
            checkParameter(AlarmRecipient_ID, strAlarmRecipientId);
            //转换为uuid
            UUID id = toUUID(strAlarmRecipientId);
            //判断是否是用户登录
            if (!getCurrentUser().getCustomerId().equals(Customer_Id)){
                AlarmRecipient alarmRecipient = checkNotNull(alarmRecipientService.findByCustomerId(getCurrentUser().getTenantId(), getCurrentUser().getCustomerId(), id));
                //检查权限
                //accessControlService.checkPermission(getCurrentUser(), Resource.ALARM_RECIPIENT, Operation.READ, alarmRecipient.getId(), alarmRecipient);
                return alarmRecipient;
            }else {
                //检查是否为空
                AlarmRecipient alarmRecipient = checkNotNull(alarmRecipientService.findById(getCurrentUser().getTenantId(), id));
                //检查权限
                //accessControlService.checkPermission(getCurrentUser(), Resource.ALARM_RECIPIENT, Operation.READ, alarmRecipient.getId(), alarmRecipient);
                return alarmRecipient;
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    //查询所有
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarmRecipient", method = RequestMethod.GET)
    @ResponseBody
    public List<AlarmRecipient> findAlarmRecipientByAll() throws ThingsboardException {
        try {
            //判断是否是用户登录
            if (!getCurrentUser().getCustomerId().equals(Customer_Id)){
                //判断获取的对象是否为空
                List<AlarmRecipient> alarmRecipients = checkNotNull(alarmRecipientService.findAllByCustomerId(getCurrentUser().getTenantId(), getCurrentUser().getCustomerId()));
                for (AlarmRecipient alarmRecipient : alarmRecipients) {
                    //检查权限
                    //accessControlService.checkPermission(getCurrentUser(), Resource.ALARM_RECIPIENT, Operation.READ, alarmRecipient.getId(), alarmRecipient);
                    String transForm = baseAlarmService.transForm(alarmRecipient.getSeverity());
                    alarmRecipient.setSeverity(transForm);
                    System.out.println("alarmRecipients="+alarmRecipient);
                }
                return alarmRecipients;
            }else {
                List<AlarmRecipient> alarmRecipients = checkNotNull(alarmRecipientService.find(getCurrentUser().getTenantId()));
                for (AlarmRecipient alarmRecipient : alarmRecipients) {
                    //accessControlService.checkPermission(getCurrentUser(), Resource.ALARM_RECIPIENT, Operation.READ, alarmRecipient.getId(), alarmRecipient);
                    String transForm = baseAlarmService.transForm(alarmRecipient.getSeverity());
                    alarmRecipient.setSeverity(transForm);
                    System.out.println("alarmRecipients="+alarmRecipient);
                }
                return alarmRecipients;
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
