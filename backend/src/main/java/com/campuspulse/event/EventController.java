package com.campuspulse.event;

import com.campuspulse.behavior.BehaviorController.BehaviorRequest;
import com.campuspulse.behavior.BehaviorService;
import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.web.bind.annotation.*;

/** Compatibility endpoint with the same validation as /api/behavior. */
@RestController
@RequestMapping("/api/events")
public class EventController {
    private final BehaviorService service;
    public EventController(BehaviorService service) {this.service=service;}
    @PostMapping public Api.ApiResponse<Void> log(@RequestBody EventRequest r) {
        service.log(AuthContext.requireUser().id(),r==null?null:new BehaviorRequest("ACTIVITY",r.activityId(),r.eventType(),"legacy",null,null,null,r.extraJson()));
        return Api.ok();
    }
    public record EventRequest(Long activityId,String eventType,String extraJson) {}
}
