package com.sso.controller;

import com.sso.dto.CreateUserRequest;
import com.sso.dto.UserResponse;
import com.sso.entity.User;
import com.sso.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orgs/{slug}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@PathVariable String slug,
                               @Valid @RequestBody CreateUserRequest req) {
        User user = userService.create(slug, req);
        return toResponse(user);
    }

    @GetMapping
    public List<UserResponse> list(@PathVariable String slug) {
        return userService.listByOrg(slug).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String slug, @PathVariable UUID userId) {
        userService.deactivate(slug, userId);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId().toString(), u.getEmail(),
                u.getOrgRole().name(), u.isActive());
    }
}
