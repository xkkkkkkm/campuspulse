package com.campuspulse.activity;

import com.campuspulse.common.Api;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.campuspulse.activity.ActivityService.*;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final ActivityService service;
    public ActivityController(ActivityService service) {this.service=service;}

    @GetMapping
    public Api.ApiResponse<PageResult<ActivityCard>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "upcoming") String status,
            @RequestParam(defaultValue = "time") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return service.list(keyword, tag, category, status, sort, page, size);
    }

    @GetMapping("/highlights")
    public Api.ApiResponse<List<ActivityCard>> highlights(@RequestParam(defaultValue = "12") int size) {
        return service.highlights(size);
    }

    @GetMapping("/{id}")
    public Api.ApiResponse<ActivityDetail> detail(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping
    public Api.ApiResponse<Map<String, Object>> create(@RequestBody UpsertActivityRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Api.ApiResponse<Void> update(@PathVariable long id, @RequestBody UpsertActivityRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public Api.ApiResponse<Void> delete(@PathVariable long id) {
        return service.delete(id);
    }

    @PostMapping("/{id}/favorite")
    public Api.ApiResponse<Map<String, Object>> toggleFavorite(@PathVariable long id, @RequestParam(required = false) Boolean favorited) {
        return service.toggleFavorite(id, favorited);
    }

    @PostMapping("/{id}/register")
    public Api.ApiResponse<Void> register(@PathVariable long id, @RequestBody RegisterActivityRequest req) {
        return service.register(id, req);
    }

    @PostMapping("/{id}/cancel-registration")
    public Api.ApiResponse<Void> cancelRegistration(@PathVariable long id) {
        return service.cancelRegistration(id);
    }

    @GetMapping("/{id}/my-registration")
    public Api.ApiResponse<MyRegistration> myRegistration(@PathVariable long id) {
        return service.myRegistration(id);
    }

    @GetMapping("/{id}/organizer-contact")
    public Api.ApiResponse<OrganizerContact> organizerContact(@PathVariable long id) {
        return service.organizerContact(id);
    }

    public Api.ApiResponse<List<RegistrationViewItem>> registrationsForOrganizer(long id) { return service.registrationsForOrganizer(id); }

    @GetMapping("/{id}/registrations")
    public Api.ApiResponse<List<RegistrationViewItem>> registrationsForOrganizer(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page,@RequestParam(defaultValue = "100") int size) {
        return service.registrationsForOrganizer(id,page,size);
    }

    @PostMapping("/{activityId}/registrations/{registrationId}/approve")
    public Api.ApiResponse<Void> approveRegistration(@PathVariable long activityId, @PathVariable long registrationId) {
        return service.approveRegistration(activityId, registrationId);
    }

    @PostMapping("/{activityId}/registrations/{registrationId}/reject")
    public Api.ApiResponse<Void> rejectRegistration(@PathVariable long activityId, @PathVariable long registrationId) {
        return service.rejectRegistration(activityId, registrationId);
    }

    @GetMapping("/{id}/teams")
    public Api.ApiResponse<List<TeamBrief>> activityTeams(@PathVariable long id) {
        return service.activityTeams(id);
    }
}
