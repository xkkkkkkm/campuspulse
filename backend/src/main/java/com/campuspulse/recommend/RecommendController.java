package com.campuspulse.recommend;

import com.campuspulse.common.Api;
import com.campuspulse.recommend.RecommendService.ActivityItem;
import com.campuspulse.recommend.RecommendService.TeamItem;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendController {
    private final RecommendService service;
    public RecommendController(RecommendService service) {this.service=service;}
    @GetMapping({"/feed","/activities"}) public Api.ApiResponse<List<ActivityItem>> activities(@RequestParam(defaultValue="12") int size) {return service.activities(size);}
    @GetMapping("/teams") public Api.ApiResponse<List<TeamItem>> teams(@RequestParam(defaultValue="8") int size) {return service.teams(size);}
}
