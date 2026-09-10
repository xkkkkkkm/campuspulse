package com.campuspulse.behavior;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/behavior")
public class BehaviorController {
    private final BehaviorService service;
    public BehaviorController(BehaviorService service) {this.service=service;}
    @PostMapping public Api.ApiResponse<Void> log(@RequestBody BehaviorRequest req) {
        service.log(AuthContext.requireUser().id(),req); return Api.ok();
    }
    public record BehaviorRequest(String targetType,Long targetId,String eventType,String scene,
            String query,String requestId,Integer rankPosition,String extraJson) {}
}
