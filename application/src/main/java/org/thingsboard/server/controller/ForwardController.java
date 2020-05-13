package org.thingsboard.server.controller;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.thingsboard.server.dao.forward.ForwardService;


@RestController
@RequestMapping("/forward")
public class ForwardController {
    @Autowired
    protected ForwardService forwardService;
    @RequestMapping(value = "/getKitToken", method = RequestMethod.POST)
    @ResponseBody
    public String getkitToken(@RequestBody JSONObject param){

        return forwardService.getaccessToken(param);
    }
}
