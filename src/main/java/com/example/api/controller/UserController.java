package com.example.api.controller;

import com.example.api.model.UserCreateRequest;
import com.example.api.model.UserResponse;
import com.example.api.model.UserUpdateRequest;
import com.example.api.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ユーザー管理の REST API コントローラー。
 *
 * <p>@Validated をクラスに付与する理由:
 * @RequestBody のバリデーションは @Valid だけで動作するが、
 * @RequestParam / @PathVariable のバリデーション（@Min / @Max 等）は
 * クラスレベルの @Validated がないと有効にならない。
 * 違反時は ConstraintViolationException がスローされ、GlobalExceptionHandler で 400 を返す。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    /**
     * POST /users: ユーザーを新規作成する。
     *
     * <p>@ResponseStatus(HttpStatus.CREATED) により、成功時は HTTP 201 Created を返す。
     * @Valid によりリクエストボディ（UserCreateRequest）のバリデーションを実行する。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    /**
     * GET /users?limit={n}: ユーザー一覧を取得する。
     *
     * <p>limit のデフォルトは 50。@Min / @Max でクエリパラメータの値域を検証する。
     * 違反時は GlobalExceptionHandler が 400 を返す。
     */
    @GetMapping
    public List<UserResponse> listUsers(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return userService.list(limit);
    }

    /**
     * GET /users/{email}: 指定メールアドレスのユーザーを取得する。
     *
     * <p>存在しない場合は UserService → UserNotFoundException →
     * GlobalExceptionHandler の順で 404 Not Found を返す。
     */
    @GetMapping("/{email}")
    public UserResponse getUser(@PathVariable String email) {
        return userService.get(email);
    }

    /**
     * PUT /users/{email}: ユーザーの name を更新する。
     *
     * <p>更新可能なフィールドは name のみ（email はパスパラメータで指定）。
     */
    @PutMapping("/{email}")
    public UserResponse updateUser(
            @PathVariable String email,
            @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(email, request);
    }
}