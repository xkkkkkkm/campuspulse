package com.campuspulse.team;

import com.campuspulse.common.Api;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.campuspulse.team.TeamService.*;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private final TeamService service;
    public TeamController(TeamService service) {this.service=service;}

    @GetMapping
    public Api.ApiResponse<List<TeamCard>> list(@RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String tag, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(activityId, keyword, tag, page, size);
    }

    @GetMapping("/page")
    public Api.ApiResponse<TeamPage> page(@RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String tag, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return service.page(activityId, keyword, tag, page, size);
    }

    @GetMapping("/{id}")
    public Api.ApiResponse<TeamDetail> detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping
    public Api.ApiResponse<Map<String, Object>> create(@RequestBody CreateTeamRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Api.ApiResponse<Void> update(@PathVariable long id, @RequestBody UpdateTeamRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public Api.ApiResponse<Void> delete(@PathVariable long id) {
        return service.delete(id);
    }

    @PostMapping("/{id}/join")
    public Api.ApiResponse<Void> requestJoin(@PathVariable long id, @RequestBody JoinRequest req) {
        return service.requestJoin(id, req);
    }

    @PostMapping("/{id}/cancel-request")
    public Api.ApiResponse<Void> cancelJoinRequest(@PathVariable long id) {
        return service.cancelJoinRequest(id);
    }

    @GetMapping("/{id}/requests")
    public Api.ApiResponse<List<JoinRequestItem>> listRequests(@PathVariable long id) {
        return service.listRequests(id);
    }

    @PostMapping("/{teamId}/requests/{requestId}/approve")
    public Api.ApiResponse<Void> approve(@PathVariable long teamId, @PathVariable long requestId) {
        return service.approve(teamId, requestId);
    }

    @PostMapping("/{teamId}/requests/{requestId}/reject")
    public Api.ApiResponse<Void> reject(@PathVariable long teamId, @PathVariable long requestId) {
        return service.reject(teamId, requestId);
    }

    @PostMapping("/{id}/leave")
    public Api.ApiResponse<Void> leave(@PathVariable long id) {
        return service.leave(id);
    }

    @PostMapping("/{id}/transfer")
    public Api.ApiResponse<Void> transfer(@PathVariable long id, @RequestBody TransferRequest req) {
        return service.transfer(id, req);
    }

    @PostMapping("/{id}/close")
    public Api.ApiResponse<Void> close(@PathVariable long id) {
        return service.close(id);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Api.ApiResponse<Void> removeMember(@PathVariable long id, @PathVariable long userId) {
        return service.removeMember(id, userId);
    }
}
